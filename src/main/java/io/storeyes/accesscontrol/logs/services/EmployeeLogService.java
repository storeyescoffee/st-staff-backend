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
import io.storeyes.accesscontrol.logs.dto.PunchMethod;
import io.storeyes.accesscontrol.logs.dto.PunchResponse;
import io.storeyes.accesscontrol.logs.dto.PunchTarget;
import io.storeyes.accesscontrol.logs.dto.ShiftHistoryGroupResponse;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.logs.entities.EmployeeLogsHistory;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogRepository;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogsHistoryRepository;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleResponse;
import io.storeyes.accesscontrol.notificationrules.services.NotificationRuleService;
import io.storeyes.accesscontrol.schedules.entities.Schedule;
import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleDetailRepository;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleRepository;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import io.storeyes.accesscontrol.workmodes.exceptions.WorkModeNotFoundException;
import io.storeyes.accesscontrol.workmodes.repositories.WorkModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final WorkModeRepository workModeRepository;
    private final EmployeeLogsHistoryRepository employeeLogsHistoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<EmployeeLogResponse> getLogsForDate(LocalDate date) {
        // Existing logs keyed by employee id
        Map<UUID, EmployeeLog> logsByEmployee = employeeLogRepository.findByDate(date)
                .stream()
                .collect(Collectors.toMap(l -> l.getEmployee().getId(), l -> l));

        // Work mode per employee from the active schedule for this date
        Map<UUID, WorkMode> workModeByEmployee = resolveScheduledWorkModes(date);

        return employeeRepository.findAllByDeletedFalse(Sort.by(Sort.Direction.ASC, "displayOrder")).stream()
                .filter(emp -> !isUnnamed(emp))
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

    /**
     * Punch history for a date, grouped by shift: each shift punched that day gets its latest {@code IN}
     * batch and its latest {@code OUT} batch. Untargeted (legacy) batches carry no shift or direction and
     * are excluded. Shifts are ordered by their most recent activity that day.
     */
    @Transactional(readOnly = true)
    public List<ShiftHistoryGroupResponse> getHistoryForDate(LocalDate date) {
        record ShiftKey(String name, LocalTime start, LocalTime end) {}

        Map<ShiftKey, EmployeeLogsHistory> latestIn = new java.util.LinkedHashMap<>();
        Map<ShiftKey, EmployeeLogsHistory> latestOut = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<ShiftKey> shiftOrder = new java.util.LinkedHashSet<>();

        // Rows come back newest-first, so the first row seen per (shift, direction) is its latest batch.
        for (EmployeeLogsHistory h : employeeLogsHistoryRepository.findByDateOrderByCreatedAtDesc(date)) {
            if (h.getMethod() == null) continue;
            ShiftKey key = new ShiftKey(h.getShiftName(), h.getShiftStartTime(), h.getShiftEndTime());
            shiftOrder.add(key);
            Map<ShiftKey, EmployeeLogsHistory> bucket = h.getMethod() == PunchMethod.IN ? latestIn : latestOut;
            bucket.putIfAbsent(key, h);
        }

        return shiftOrder.stream()
                .map(key -> new ShiftHistoryGroupResponse(
                        new ShiftHistoryGroupResponse.ShiftInfo(key.name(), key.start(), key.end()),
                        toDirection(latestIn.get(key)),
                        toDirection(latestOut.get(key))))
                .toList();
    }

    private ShiftHistoryGroupResponse.Direction toDirection(EmployeeLogsHistory h) {
        if (h == null) return null;
        return new ShiftHistoryGroupResponse.Direction(
                objectMapper.readTree(h.getLogs()),
                h.getNotification() == null ? null : objectMapper.readTree(h.getNotification()));
    }

    /**
     * An employee auto-created from a punch for an unknown code carries the code as its name, since a
     * punch carries no name. Those placeholders stay out of the attendance list until someone names them.
     */
    private boolean isUnnamed(Employee emp) {
        String name = emp.getName();
        String code = emp.getCode();
        if (name == null || code == null) return false;
        return name.trim().equalsIgnoreCase(code.trim());
    }

    @Transactional
    public PunchResponse processPunches(
            LocalDate date,
            LocalTime time,
            PunchTarget target,
            List<EmployeeUpsert> employees,
            List<PunchEntry> punches) {
        WorkMode shift = null;
        PunchResponse response;

        if (target != null && target.shiftId() != null && target.method() != null) {
            shift = workModeRepository.findById(target.shiftId())
                    .orElseThrow(() -> new WorkModeNotFoundException(target.shiftId()));
            response = processTargetedPunches(date, time, target, shift, employees, punches);
        } else {
            response = processAllPunches(date, time, employees, punches);
        }

        saveHistory(date, target, shift, response);
        return response;
    }

    /**
     * A single live event pushed straight from a device (Hikvision HTTP host notification), for one
     * employee. First accepted event of the day for that employee opens the log as a check-in; the
     * next event closes it. Unlike the batch paths there is no absence / missed-out sweep — only the
     * one employee is ever touched.
     *
     * <p>Mirrors the per-punch body of {@link #processAllPunches}: insert-first for an unknown code,
     * a soft-deleted code is ignored, a later punch closes the log with an overnight-aware duration,
     * and an event that merely echoes the recorded check-in time does nothing.
     */
    @Transactional
    public PunchResponse processDeviceEvent(LocalDate date, LocalTime time, String employeeCode) {
        String code = employeeCode.trim();

        Optional<Employee> existingByCode = employeeRepository.findByCode(code);
        if (existingByCode.map(Employee::isDeleted).orElse(false)) {
            return new PunchResponse(List.of(), null); // soft-deleted — ignore this event
        }
        Employee employee = existingByCode.orElseGet(() -> employeeRepository.save(Employee.builder()
                .name(code)
                .code(code)
                .build()));

        Optional<EmployeeLog> existing = employeeLogRepository.findByDateAndEmployee_Id(date, employee.getId());
        PunchMethod method;
        EmployeeLog saved;

        if (existing.isEmpty()) {
            WorkMode wm = resolveScheduledWorkModes(date).get(employee.getId());
            saved = employeeLogRepository.save(applyInPunch(date, employee, wm, time));
            createAnomalyIfNeeded(saved);
            method = PunchMethod.IN;
        } else {
            EmployeeLog log = existing.get();
            // Nothing to do: no open check-in, the day is already closed, or the event echoes the check-in.
            if (log.getLoggedIn() == null || log.getLoggedOut() != null || time.equals(log.getLoggedIn())) {
                return new PunchResponse(List.of(), null);
            }
            long minutes = ChronoUnit.MINUTES.between(log.getLoggedIn(), time);
            if (minutes < 0) minutes += 24 * 60; // overnight shift
            log.setLoggedOut(time);
            log.setDuration((int) minutes);
            saved = employeeLogRepository.save(log);
            method = PunchMethod.OUT;
        }

        List<EmployeeLogResponse> results = List.of(EmployeeLogResponse.from(saved));
        PunchResponse response = new PunchResponse(results, buildNotifications(time, results));

        WorkMode shift = saved.getWorkMode();
        saveHistory(date, new PunchTarget(shift != null ? shift.getId() : null, method), shift, response);
        return response;
    }

    /** Snapshot of this punch batch — logs produced and notifications computed — kept for audit purposes. */
    private void saveHistory(LocalDate date, PunchTarget target, WorkMode shift, PunchResponse response) {
        employeeLogsHistoryRepository.save(EmployeeLogsHistory.builder()
                .date(date)
                .shiftName(shift != null ? shift.getName() : null)
                .shiftStartTime(shift != null ? shift.getStartTime() : null)
                .shiftEndTime(shift != null ? shift.getEndTime() : null)
                .method(target != null ? target.method() : null)
                .logs(objectMapper.writeValueAsString(response.logs()))
                .notification(response.notifications() == null
                        ? null
                        : objectMapper.writeValueAsString(response.notifications()))
                .build());
    }

    /**
     * Batch scoped to one shift and one direction.
     *
     * <p>Only employees scheduled on {@code target.shiftId} for this date are considered; punches for
     * anyone else are ignored. {@code IN} decides PRESENT / LATE / ABSENT, {@code OUT} decides
     * MISSED_OUT — neither direction writes the other's statuses.
     */
    private PunchResponse processTargetedPunches(
            LocalDate date,
            LocalTime time,
            PunchTarget target,
            WorkMode shift,
            List<EmployeeUpsert> employees,
            List<PunchEntry> punches) {
        upsertEmployees(employees);

        // Employees whose schedule for this date puts them on the targeted shift. Anyone else — including
        // codes that arrived in the punch list — is outside this check and is left untouched.
        Map<UUID, WorkMode> workModeByEmployee = resolveScheduledWorkModes(date);
        Map<UUID, Employee> shiftEmployees = employeeRepository.findAllByDeletedFalse().stream()
                .filter(emp -> {
                    WorkMode wm = workModeByEmployee.get(emp.getId());
                    return wm != null && shift.getId().equals(wm.getId());
                })
                .collect(Collectors.toMap(Employee::getId, emp -> emp));

        // Punches that belong to the shift, keyed by employee. A code can punch more than once in one
        // batch (e.g. a raw full-day device dump); resolvePunchTime decides which one counts.
        Map<UUID, List<LocalTime>> punchesByEmployee = new java.util.LinkedHashMap<>();
        for (PunchEntry punch : punches == null ? List.<PunchEntry>of() : punches) {
            String code = punch.employeeCode().trim();
            employeeRepository.findByCode(code)
                    .filter(emp -> shiftEmployees.containsKey(emp.getId()))
                    .ifPresent(emp -> punchesByEmployee
                            .computeIfAbsent(emp.getId(), k -> new ArrayList<>())
                            .add(punch.time()));
        }

        Map<UUID, LocalTime> punchTimes = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, List<LocalTime>> entry : punchesByEmployee.entrySet()) {
            punchTimes.put(entry.getKey(), resolvePunchTime(entry.getValue(), target.method(), shift));
        }

        List<EmployeeLogResponse> results = new ArrayList<>();

        for (Employee emp : shiftEmployees.values()) {
            Optional<EmployeeLog> existing = employeeLogRepository.findByDateAndEmployee_Id(date, emp.getId());
            LocalTime x = punchTimes.get(emp.getId());

            EmployeeLog changed = target.method() == PunchMethod.IN
                    ? applyTargetedIn(date, emp, shift, existing, x)
                    : applyTargetedOut(existing, x);

            if (changed == null) continue;

            EmployeeLog saved = employeeLogRepository.save(changed);
            createAnomalyIfNeeded(saved);
            results.add(EmployeeLogResponse.from(saved));
        }

        return new PunchResponse(results, buildNotifications(time, results));
    }

    /**
     * IN side for one employee on the targeted shift. Returns the log to persist, or null when there is
     * nothing to change.
     *
     * <p>A punch checks the employee in (PRESENT or LATE, per the shift's tolerance), including when an
     * earlier IN check had already written an ABSENT for them — arriving late overwrites that absence.
     * No punch and no log yet means they never showed: ABSENT, but only on a followed-up shift.
     */
    private EmployeeLog applyTargetedIn(
            LocalDate date, Employee emp, WorkMode shift, Optional<EmployeeLog> existing, LocalTime x) {
        if (x != null) {
            // Already checked in — a second IN punch never moves the recorded time.
            if (existing.isPresent() && existing.get().getLoggedIn() != null) return null;

            if (existing.isEmpty()) return applyInPunch(date, emp, shift, x);

            EmployeeLog log = existing.get();
            log.setLoggedIn(x);
            log.setStatus(statusForIn(shift, x));
            return log;
        }

        // No punch: absent, unless they already have a log (from this or an earlier check).
        if (existing.isPresent()) return null;
        if (!shift.isFollowedUp()) return null;

        return EmployeeLog.builder()
                .date(date)
                .employee(emp)
                .workMode(shift)
                .status(LogStatus.ABSENT)
                .build();
    }

    /**
     * OUT side for one employee on the targeted shift. Returns the log to persist, or null when there is
     * nothing to change.
     *
     * <p>Only an open log — logged in, not yet out — can be closed by a punch or flagged MISSED_OUT
     * without one. Someone who never logged in (no log, or an ABSENT one) is left alone.
     */
    private EmployeeLog applyTargetedOut(Optional<EmployeeLog> existing, LocalTime x) {
        if (existing.isEmpty()) return null;

        EmployeeLog log = existing.get();
        if (log.getLoggedIn() == null || log.getLoggedOut() != null) return null;

        // No punch, or the only punch is an echo of the check-in read: no real check-out occurred.
        if (x == null || x.equals(log.getLoggedIn())) {
            log.setStatus(LogStatus.MISSED_OUT);
            return log;
        }

        long minutes = ChronoUnit.MINUTES.between(log.getLoggedIn(), x);
        if (minutes < 0) minutes += 24 * 60; // overnight shift
        log.setLoggedOut(x);
        log.setDuration((int) minutes);
        return log;
    }

    /**
     * Resolves the one punch time to use for an employee who may appear more than once in a batch
     * (e.g. a raw full-day device dump). IN keeps the earliest arrival; OUT keeps the punch nearest the
     * shift's end, so an earlier echo of the check-in doesn't get mistaken for the check-out.
     */
    private LocalTime resolvePunchTime(List<LocalTime> times, PunchMethod method, WorkMode shift) {
        if (times.size() == 1) return times.get(0);
        if (method == PunchMethod.IN) return times.stream().min(LocalTime::compareTo).orElseThrow();

        LocalTime endTime = shift.getEndTime();
        if (endTime == null) return times.stream().max(LocalTime::compareTo).orElseThrow();
        return times.stream()
                .min(Comparator.comparingLong(t -> Math.abs(ChronoUnit.MINUTES.between(endTime, t))))
                .orElseThrow();
    }

    /** Legacy untargeted batch: sweeps every scheduled employee for both absence and missing-out. */
    private PunchResponse processAllPunches(
            LocalDate date, LocalTime time, List<EmployeeUpsert> employees, List<PunchEntry> punches) {
        upsertEmployees(employees);

        Map<UUID, WorkMode> workModeByEmployee = resolveScheduledWorkModes(date);
        List<EmployeeLogResponse> results = new ArrayList<>();

        java.util.Set<String> processedCodes = new java.util.HashSet<>();

        for (PunchEntry punch : punches) {
            LocalTime x = punch.time();
            String code = punch.employeeCode().trim();

            // Insert-first: a punch for a code we've never seen creates the employee
            // (name defaults to the code, since a punch carries no name) instead of 404ing.
            // Codes present in employees[] were already created with their real name above.
            Optional<Employee> existingByCode = employeeRepository.findByCode(code);
            if (existingByCode.map(Employee::isDeleted).orElse(false)) continue; // soft-deleted — ignore this punch

            Employee employee = existingByCode
                    .orElseGet(() -> employeeRepository.save(Employee.builder()
                            .name(code)
                            .code(code)
                            .build()));

            Optional<EmployeeLog> existing = employeeLogRepository.findByDateAndEmployee_Id(date, employee.getId());

            // Ignore punch if it duplicates the loggedIn time — do NOT add to processedCodes
            if (existing.isPresent() && x.equals(existing.get().getLoggedIn())) continue;

            processedCodes.add(code);

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

        for (Employee emp : employeeRepository.findAllByDeletedFalse()) {
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
        return EmployeeLog.builder()
                .date(date)
                .employee(employee)
                .workMode(wm)
                .loggedIn(x)
                .status(statusForIn(wm, x))
                .build();
    }

    /** LATE once the punch reaches start + tolerance; PRESENT otherwise, and always when tracking is off. */
    private LogStatus statusForIn(WorkMode wm, LocalTime x) {
        if (wm == null || !wm.isFollowedUp() || wm.getStartTime() == null) return LogStatus.PRESENT;

        int tl = wm.getTolerantLate() != null ? wm.getTolerantLate() : 0;
        LocalTime cutoffPresent = wm.getStartTime().plusMinutes(tl);
        return x.isBefore(cutoffPresent) ? LogStatus.PRESENT : LogStatus.LATE;
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
