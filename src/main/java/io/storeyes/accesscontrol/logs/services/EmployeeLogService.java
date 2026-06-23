package io.storeyes.accesscontrol.logs.services;

import io.storeyes.accesscontrol.anomalies.entities.Anomaly;
import io.storeyes.accesscontrol.anomalies.entities.AnomalyType;
import io.storeyes.accesscontrol.anomalies.repositories.AnomalyRepository;
import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.employees.repositories.EmployeeRepository;
import io.storeyes.accesscontrol.employees.entities.Credential;
import io.storeyes.accesscontrol.logs.dto.EmployeeLogResponse;
import io.storeyes.accesscontrol.logs.dto.EmployeeUpsert;
import io.storeyes.accesscontrol.logs.dto.NotificationBatch;
import io.storeyes.accesscontrol.logs.dto.PunchEntry;
import io.storeyes.accesscontrol.logs.dto.PunchResponse;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogRepository;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleResponse;
import io.storeyes.accesscontrol.notificationrules.services.NotificationRuleService;
import io.storeyes.accesscontrol.schedules.entities.Schedule;
import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleDetailRepository;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleRepository;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeLogService {

    private static final LocalTime DND_START = LocalTime.of(23, 0);
    private static final LocalTime DND_END = LocalTime.of(6, 30);

    private final EmployeeLogRepository employeeLogRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleDetailRepository scheduleDetailRepository;
    private final AnomalyRepository anomalyRepository;
    private final NotificationRuleService notificationRuleService;

    @Transactional(readOnly = true)
    public List<EmployeeLogResponse> getLogsForDate(LocalDate date) {
        // Existing logs keyed by employee id
        Map<UUID, EmployeeLog> logsByEmployee = employeeLogRepository.findByDate(date)
                .stream()
                .collect(Collectors.toMap(l -> l.getEmployee().getId(), l -> l));

        // Work mode per employee from the active schedule for this date
        Map<UUID, WorkMode> workModeByEmployee = resolveScheduledWorkModes(date);

        return employeeRepository.findAll().stream()
                .map(emp -> {
                    EmployeeLog log = logsByEmployee.get(emp.getId());
                    if (log != null) {
                        return EmployeeLogResponse.from(log);
                    }
                    WorkMode wm = workModeByEmployee.get(emp.getId());
                    return EmployeeLogResponse.stub(date, emp, wm);
                })
                .toList();
    }

    @Transactional
    public PunchResponse processPunches(
            LocalDate date, LocalTime time, List<EmployeeUpsert> employees, List<PunchEntry> punches) {
        upsertEmployees(employees);

        Map<UUID, WorkMode> workModeByEmployee = resolveScheduledWorkModes(date);
        List<EmployeeLogResponse> results = new ArrayList<>();

        java.util.Set<String> processedCodes = new java.util.HashSet<>();

        for (PunchEntry punch : punches) {
            LocalTime x = punch.time();

            Employee employee = employeeRepository.findByCode(punch.employeeCode())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Employee not found: " + punch.employeeCode()));

            Optional<EmployeeLog> existing = employeeLogRepository.findByDateAndEmployee_Id(date, employee.getId());

            // Ignore punch if it duplicates the loggedIn time — do NOT add to processedCodes
            if (existing.isPresent() && x.equals(existing.get().getLoggedIn())) continue;

            processedCodes.add(punch.employeeCode());

            if (existing.isPresent()) {
                EmployeeLog log = existing.get();
                if (log.getLoggedIn() != null && log.getLoggedOut() == null) {
                    long minutes = ChronoUnit.MINUTES.between(log.getLoggedIn(), x);
                    if (minutes < 0) minutes += 24 * 60; // overnight shift
                    log.setLoggedOut(x);
                    log.setDuration((int) minutes);
                    results.add(EmployeeLogResponse.from(employeeLogRepository.save(log)));
                }
                // loggedOut already set → skip
            } else {
                WorkMode wm = workModeByEmployee.get(employee.getId());
                EmployeeLog log = applyInPunch(date, employee, wm, x);
                EmployeeLog saved = employeeLogRepository.save(log);
                createAnomalyIfNeeded(saved);
                results.add(EmployeeLogResponse.from(saved));
            }
        }

        // Mark MISSED_OUT for employees not in this request who have loggedIn but no loggedOut
        employeeLogRepository.findByDate(date).stream()
                .filter(log -> !processedCodes.contains(log.getEmployee().getCode()))
                .filter(log -> log.getLoggedIn() != null && log.getLoggedOut() == null)
                .forEach(log -> {
                    log.setStatus(LogStatus.MISSED_OUT);
                    EmployeeLog saved = employeeLogRepository.save(log);
                    createAnomalyIfNeeded(saved);
                });

        // Mark ABSENT for scheduled employees who:
        //   1. are not in the request body
        //   2. have no existing log for this date
        //   3. have a followed-up work mode whose time range covers the punch timestamp
        java.util.Set<String> requestCodes = punches.stream()
                .map(p -> p.employeeCode().trim())
                .collect(Collectors.toSet());

        for (Employee emp : employeeRepository.findAll()) {
            if (requestCodes.contains(emp.getCode())) continue;
            if (employeeLogRepository.findByDateAndEmployee_Id(date, emp.getId()).isPresent()) continue;

            WorkMode wm = workModeByEmployee.get(emp.getId());
            if (wm == null || !wm.isFollowedUp() || wm.getStartTime() == null || wm.getEndTime() == null) continue;

            // batch timestamp must fall within the shift window [startTime, endTime]
            if (time.isBefore(wm.getStartTime()) || time.isAfter(wm.getEndTime())) continue;

            EmployeeLog absent = EmployeeLog.builder()
                    .date(date)
                    .employee(emp)
                    .workMode(wm)
                    .status(LogStatus.ABSENT)
                    .build();
            EmployeeLog saved = employeeLogRepository.save(absent);
            createAnomalyIfNeeded(saved);
            results.add(EmployeeLogResponse.from(saved));
        }

        return new PunchResponse(results, buildNotifications(time, results));
    }

    /**
     * Turn the changed logs into notification instructions, honoring the tenant's rules:
     * late/absence gate which statuses qualify, dnd suppresses (to defer) during the night
     * window, and group signals one summary instead of one notification per employee.
     */
    private NotificationBatch buildNotifications(LocalTime checkTime, List<EmployeeLogResponse> results) {
        Map<String, Boolean> rules = notificationRuleService.findAll().stream()
                .collect(Collectors.toMap(NotificationRuleResponse::id, NotificationRuleResponse::enabled));
        boolean lateOn = rules.getOrDefault("late", false);
        boolean absenceOn = rules.getOrDefault("absence", false);
        boolean grouped = rules.getOrDefault("group", false);
        boolean dndOn = rules.getOrDefault("dnd", false);

        List<NotificationBatch.Item> items = results.stream()
                .filter(r -> (r.status() == LogStatus.LATE && lateOn)
                        || (r.status() == LogStatus.ABSENT && absenceOn))
                .map(r -> new NotificationBatch.Item(
                        r.employee().code(), r.employee().name(), r.status()))
                .toList();

        int lateCount = (int) items.stream().filter(i -> i.status() == LogStatus.LATE).count();
        int absenceCount = (int) items.stream().filter(i -> i.status() == LogStatus.ABSENT).count();

        boolean dndSuppressed = dndOn && !items.isEmpty() && inDndWindow(checkTime);
        boolean send = !items.isEmpty() && !dndSuppressed;

        return new NotificationBatch(send, dndSuppressed, grouped, lateCount, absenceCount,
                buildSummary(lateCount, absenceCount), items);
    }

    /** DND window spans midnight: [23:00, 24:00) ∪ [00:00, 06:30]. */
    private boolean inDndWindow(LocalTime t) {
        return !t.isBefore(DND_START) || !t.isAfter(DND_END);
    }

    private String buildSummary(int lateCount, int absenceCount) {
        List<String> parts = new ArrayList<>();
        if (lateCount > 0) parts.add(lateCount + " late");
        if (absenceCount > 0) parts.add(absenceCount + " absent");
        return String.join(" · ", parts);
    }

    /** Create any employee whose code is not yet known. Existing codes are left untouched. */
    private void upsertEmployees(List<EmployeeUpsert> employees) {
        if (employees == null || employees.isEmpty()) return;

        java.util.Set<String> requestedCodes = employees.stream()
                .filter(e -> e != null && e.code() != null && !e.code().isBlank())
                .map(e -> e.code().trim())
                .collect(Collectors.toSet());
        if (requestedCodes.isEmpty()) return;

        // One query for all existing codes, instead of one existence check per employee.
        java.util.Set<String> existingCodes =
                new java.util.HashSet<>(employeeRepository.findExistingCodes(requestedCodes));

        for (EmployeeUpsert e : employees) {
            if (e == null || e.code() == null || e.code().isBlank()) continue;
            String code = e.code().trim();
            if (!existingCodes.add(code)) continue; // already in DB or a duplicate within this batch

            java.util.Set<Credential> credentials = e.credentials() == null
                    ? new java.util.LinkedHashSet<>()
                    : new java.util.LinkedHashSet<>(e.credentials());

            employeeRepository.save(Employee.builder()
                    .name(e.name())
                    .code(code)
                    .credentials(credentials)
                    .build());
        }
    }

    private void createAnomalyIfNeeded(EmployeeLog log) {
        AnomalyType type = switch (log.getStatus()) {
            case LATE      -> AnomalyType.LATE;
            case ABSENT    -> AnomalyType.ABSENCE;
            case MISSED_OUT -> AnomalyType.MISSING_OUT;
            default        -> null;
        };
        if (type == null || anomalyRepository.existsByEmployeeLog(log)) return;
        anomalyRepository.save(Anomaly.builder()
                .employeeLog(log)
                .type(type)
                .isHandled(false)
                .build());
    }

    private EmployeeLog applyInPunch(LocalDate date, Employee employee, WorkMode wm, LocalTime x) {
        EmployeeLog.EmployeeLogBuilder builder = EmployeeLog.builder()
                .date(date)
                .employee(employee)
                .workMode(wm);

        if (wm != null && wm.isFollowedUp() && wm.getStartTime() != null) {
            int tl = wm.getTolerantLate() != null ? wm.getTolerantLate() : 0;
            LocalTime cutoffPresent = wm.getStartTime().plusMinutes(tl);

            if (x.isBefore(cutoffPresent)) {
                builder.loggedIn(x).status(LogStatus.PRESENT);
            } else {
                builder.loggedIn(x).status(LogStatus.LATE);
            }
        } else {
            // tracking off → always PRESENT
            builder.loggedIn(x).status(LogStatus.PRESENT);
        }

        return builder.build();
    }


    /** Returns the scheduled WorkMode (possibly null for rest day) per employee for the given date. */
    private Map<UUID, WorkMode> resolveScheduledWorkModes(LocalDate date) {
        List<Schedule> schedules = scheduleRepository
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);

        if (schedules.isEmpty()) {
            return Map.of();
        }

        // Use the most recently started schedule if multiple overlap
        Schedule schedule = schedules.stream()
                .max((a, b) -> {
                    if (a.getStartDate() == null) return -1;
                    if (b.getStartDate() == null) return 1;
                    return a.getStartDate().compareTo(b.getStartDate());
                })
                .orElseThrow();

        List<ScheduleDetail> details = scheduleDetailRepository
                .findByScheduleIdAndDow(schedule.getId(), date.getDayOfWeek());

        return details.stream()
                .collect(Collectors.toMap(
                        d -> d.getEmployee().getId(),
                        ScheduleDetail::getWorkMode));
    }
}
