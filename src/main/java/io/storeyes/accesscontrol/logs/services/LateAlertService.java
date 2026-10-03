package io.storeyes.accesscontrol.logs.services;

import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.employees.repositories.EmployeeRepository;
import io.storeyes.accesscontrol.logs.dto.EmployeeLogResponse;
import io.storeyes.accesscontrol.logs.dto.LateAlertResponse;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogRepository;
import io.storeyes.accesscontrol.logs.repositories.ShiftAlertRepository;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleResponse;
import io.storeyes.accesscontrol.notificationrules.services.NotificationRuleService;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quarter-hourly late-arrival check, driven by the proxy backend's scheduler for each store.
 *
 * <p>An employee is due while {@code A <= now < A + 15 min}, where {@code A = shift start + timeToNotify}
 * (the work mode's "time to notify"; modes without one are never alerted). A quarter-hourly caller
 * therefore lands in that window exactly once per shift. Due employees who checked in LATE, or who have not checked in at
 * all, are alerted; PRESENT ones are not.
 *
 * <p>This is an IN-side check only. A due employee with no log yet is marked ABSENT in
 * {@code employee_logs} (and the marks are audited as an {@code IN} batch in the punch history), whatever
 * the notification rules say; a later check-in still overwrites that absence. Check-out and MISSED_OUT
 * are never touched here.
 *
 * <p>Each alert is claimed in {@code shift_alerts} before it is returned, so a re-run, a delayed tick or
 * a second backend instance never alerts the same employee's shift twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LateAlertService {

    private static final int WINDOW_MINUTES = 15;

    private final EmployeeLogService employeeLogService;
    private final EmployeeLogRepository employeeLogRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftAlertRepository shiftAlertRepository;
    private final NotificationRuleService notificationRuleService;

    @Transactional
    public LateAlertResponse claimDueAlerts(LocalDateTime now) {
        Map<String, Boolean> rules = notificationRuleService.findAll().stream()
                .collect(Collectors.toMap(NotificationRuleResponse::id, NotificationRuleResponse::enabled));
        boolean lateOn = rules.getOrDefault("late", false);
        boolean absenceOn = rules.getOrDefault("absence", false);

        List<EmployeeLogResponse> alerted = new ArrayList<>();
        // Yesterday too: a late-evening shift's window can run past midnight.
        for (LocalDate date : List.of(now.toLocalDate().minusDays(1), now.toLocalDate())) {
            for (Due due : dueOn(date, now)) {
                LogStatus status = due.log().status();
                boolean wanted = (status == LogStatus.LATE && lateOn) || (status == LogStatus.ABSENT && absenceOn);
                if (wanted && shiftAlertRepository.claim(
                        date, due.log().employee().id(), due.workModeId(), status.name()) == 1) {
                    alerted.add(due.log());
                }
            }
        }

        if (!alerted.isEmpty()) {
            log.info("Late-arrival alerts at {}: {}", now, alerted.stream()
                    .map(r -> r.employee().code() + ":" + r.status()).toList());
        }
        return new LateAlertResponse(alerted, employeeLogService.buildNotifications(now.toLocalTime(), alerted));
    }

    /** An alertable employee and the scheduled shift the alert is for (the log's own may be unset). */
    private record Due(EmployeeLogResponse log, UUID workModeId) {}

    /**
     * Employees scheduled on {@code date} whose shift is in the alert window at {@code now} and not PRESENT.
     * Those with no log yet are marked ABSENT on the way.
     */
    private List<Due> dueOn(LocalDate date, LocalDateTime now) {
        Map<UUID, WorkMode> scheduled = employeeLogService.resolveScheduledWorkModes(date);
        if (scheduled.isEmpty()) return List.of();

        Map<UUID, EmployeeLog> logsByEmployee = employeeLogRepository.findByDate(date).stream()
                .collect(Collectors.toMap(l -> l.getEmployee().getId(), l -> l));

        List<Due> due = new ArrayList<>();
        Map<WorkMode, List<EmployeeLogResponse>> marked = new LinkedHashMap<>();
        for (Employee emp : employeeRepository.findAllByDeletedFalse()) {
            WorkMode wm = scheduled.get(emp.getId());
            if (wm == null || !wm.isFollowedUp() || wm.getStartTime() == null || wm.getTimeToNotify() == null) continue;
            if (employeeLogService.isUnnamed(emp)) continue;

            LocalDateTime notifyAt = date.atTime(wm.getStartTime()).plusMinutes(wm.getTimeToNotify());
            boolean inWindow = !now.isBefore(notifyAt) && now.isBefore(notifyAt.plusMinutes(WINDOW_MINUTES));
            if (!inWindow) continue;

            EmployeeLog log = logsByEmployee.get(emp.getId());
            if (log == null) {
                log = employeeLogService.markAbsent(date, emp, wm);
                if (log == null) continue;
                marked.computeIfAbsent(wm, k -> new ArrayList<>()).add(EmployeeLogResponse.from(log));
            }
            if (log.getStatus() == LogStatus.LATE || log.getStatus() == LogStatus.ABSENT) {
                due.add(new Due(EmployeeLogResponse.from(log), wm.getId()));
            }
        }
        marked.forEach((shift, absences) -> employeeLogService.recordAbsences(date, shift, absences));
        return due;
    }
}
