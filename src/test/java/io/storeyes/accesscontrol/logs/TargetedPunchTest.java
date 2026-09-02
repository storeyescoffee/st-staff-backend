package io.storeyes.accesscontrol.logs;

import io.storeyes.accesscontrol.anomalies.repositories.AnomalyRepository;
import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.employees.repositories.EmployeeRepository;
import io.storeyes.accesscontrol.logs.dto.EmployeeLogResponse;
import io.storeyes.accesscontrol.logs.dto.PunchEntry;
import io.storeyes.accesscontrol.logs.dto.PunchMethod;
import io.storeyes.accesscontrol.logs.dto.PunchResponse;
import io.storeyes.accesscontrol.logs.dto.PunchTarget;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogRepository;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogsHistoryRepository;
import io.storeyes.accesscontrol.logs.services.EmployeeLogService;
import io.storeyes.accesscontrol.notificationrules.services.NotificationRuleService;
import io.storeyes.accesscontrol.schedules.entities.Schedule;
import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleDetailRepository;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleRepository;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import io.storeyes.accesscontrol.workmodes.exceptions.WorkModeNotFoundException;
import io.storeyes.accesscontrol.workmodes.repositories.WorkModeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Targeted punch batches: {@code target = {shiftId, method}} scopes the check to the employees scheduled
 * on one work mode, and to the statuses that direction can produce.
 */
class TargetedPunchTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 12); // a Sunday
    private static final LocalTime CHECK_TIME = LocalTime.of(14, 30);

    /** Morning shift, the target of every test here: starts 09:00, 15 min of tolerance. */
    private final WorkMode morning = WorkMode.builder()
            .id(UUID.randomUUID()).name("Morning")
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0))
            .tolerantLate(15).followedUp(true)
            .build();

    /** A second shift that must never be touched when the batch targets {@code morning}. */
    private final WorkMode evening = WorkMode.builder()
            .id(UUID.randomUUID()).name("Evening")
            .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(23, 0))
            .tolerantLate(15).followedUp(true)
            .build();

    private EmployeeLogRepository employeeLogRepository;
    private EmployeeRepository employeeRepository;
    private AnomalyRepository anomalyRepository;
    private WorkModeRepository workModeRepository;
    private EmployeeLogService service;

    /** Existing logs for DATE, keyed by employee id — the mocked DB the service reads and writes. */
    private final Map<UUID, EmployeeLog> logs = new HashMap<>();
    private final List<Employee> roster = new ArrayList<>();
    private final Map<UUID, WorkMode> scheduled = new HashMap<>();

    @BeforeEach
    void setUp() {
        employeeLogRepository = Mockito.mock(EmployeeLogRepository.class);
        employeeRepository = Mockito.mock(EmployeeRepository.class);
        anomalyRepository = Mockito.mock(AnomalyRepository.class);
        workModeRepository = Mockito.mock(WorkModeRepository.class);
        ScheduleRepository scheduleRepository = Mockito.mock(ScheduleRepository.class);
        ScheduleDetailRepository scheduleDetailRepository = Mockito.mock(ScheduleDetailRepository.class);
        NotificationRuleService notificationRuleService = Mockito.mock(NotificationRuleService.class);
        EmployeeLogsHistoryRepository employeeLogsHistoryRepository =
                Mockito.mock(EmployeeLogsHistoryRepository.class);

        service = new EmployeeLogService(
                employeeLogRepository, employeeRepository, scheduleRepository, scheduleDetailRepository,
                anomalyRepository, notificationRuleService, workModeRepository,
                employeeLogsHistoryRepository, new ObjectMapper());

        when(notificationRuleService.findAll()).thenReturn(List.of());
        when(workModeRepository.findById(morning.getId())).thenReturn(Optional.of(morning));
        when(workModeRepository.findById(evening.getId())).thenReturn(Optional.of(evening));

        when(employeeRepository.findAllByDeletedFalse()).thenReturn(roster);
        when(employeeRepository.findByCode(any())).thenAnswer(inv -> roster.stream()
                .filter(e -> e.getCode().equals(inv.getArgument(0)))
                .findFirst());

        // The schedule wiring resolveScheduledWorkModes() walks: one active schedule, one detail per employee.
        Schedule schedule = Schedule.builder().id(UUID.randomUUID()).startDate(DATE.minusDays(7)).endDate(DATE.plusDays(7)).build();
        when(scheduleRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(DATE, DATE))
                .thenReturn(List.of(schedule));
        when(scheduleDetailRepository.findByScheduleIdAndDow(schedule.getId(), DATE.getDayOfWeek()))
                .thenAnswer(inv -> roster.stream()
                        .filter(e -> scheduled.containsKey(e.getId()))
                        .map(e -> ScheduleDetail.builder()
                                .schedule(schedule).employee(e).dow(DATE.getDayOfWeek())
                                .workMode(scheduled.get(e.getId()))
                                .build())
                        .toList());

        when(employeeLogRepository.findByDateAndEmployee_Id(any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(logs.get(inv.<UUID>getArgument(1))));
        when(employeeLogRepository.findByDate(DATE)).thenAnswer(inv -> new ArrayList<>(logs.values()));
        when(employeeLogRepository.save(any())).thenAnswer(inv -> {
            EmployeeLog log = inv.getArgument(0);
            if (log.getId() == null) log.setId(UUID.randomUUID());
            logs.put(log.getEmployee().getId(), log);
            return log;
        });
    }

    /** Register an employee on the roster, scheduled on {@code wm} for DATE. */
    private Employee employee(String code, WorkMode wm) {
        Employee e = Employee.builder().id(UUID.randomUUID()).name(code).code(code).build();
        roster.add(e);
        if (wm != null) scheduled.put(e.getId(), wm);
        return e;
    }

    private void existingLog(Employee e, LocalTime in, LocalTime out, LogStatus status) {
        logs.put(e.getId(), EmployeeLog.builder()
                .id(UUID.randomUUID()).date(DATE).employee(e).workMode(scheduled.get(e.getId()))
                .loggedIn(in).loggedOut(out).status(status)
                .build());
    }

    private PunchResponse punch(WorkMode shift, PunchMethod method, PunchEntry... punches) {
        return service.processPunches(
                DATE, CHECK_TIME, new PunchTarget(shift.getId(), method), List.of(), List.of(punches));
    }

    private LogStatus statusOf(Employee e) {
        EmployeeLog log = logs.get(e.getId());
        return log == null ? null : log.getStatus();
    }

    // ---------- method = IN ----------

    @Test
    void inPunchBeforeToleranceIsPresentAndAfterItIsLate() {
        Employee onTime = employee("E1", morning);
        Employee late = employee("E2", morning);

        punch(morning, PunchMethod.IN,
                new PunchEntry("E1", LocalTime.of(8, 55)),
                new PunchEntry("E2", LocalTime.of(9, 40)));

        assertThat(statusOf(onTime)).isEqualTo(LogStatus.PRESENT);
        assertThat(statusOf(late)).isEqualTo(LogStatus.LATE);
        assertThat(logs.get(late.getId()).getLoggedIn()).isEqualTo(LocalTime.of(9, 40));
    }

    @Test
    void inPunchExactlyAtCutoffIsLate() {
        Employee e = employee("E1", morning);

        punch(morning, PunchMethod.IN, new PunchEntry("E1", LocalTime.of(9, 15)));

        assertThat(statusOf(e)).isEqualTo(LogStatus.LATE);
    }

    @Test
    void inMarksScheduledEmployeeWithNoPunchAbsent() {
        Employee noShow = employee("E1", morning);

        PunchResponse response = punch(morning, PunchMethod.IN);

        assertThat(statusOf(noShow)).isEqualTo(LogStatus.ABSENT);
        assertThat(logs.get(noShow.getId()).getLoggedIn()).isNull();
        assertThat(response.logs()).extracting(EmployeeLogResponse::status).containsExactly(LogStatus.ABSENT);
    }

    @Test
    void inIgnoresEmployeesOnOtherShiftsEntirely() {
        Employee onEvening = employee("E9", evening);
        Employee unscheduled = employee("E8", null);

        punch(morning, PunchMethod.IN,
                new PunchEntry("E9", LocalTime.of(9, 5)),   // punched, but not on the targeted shift
                new PunchEntry("E8", LocalTime.of(9, 5)));

        // Neither the punch nor an absence sweep may write anything for them.
        assertThat(logs).isEmpty();
        verify(employeeLogRepository, never()).save(any());
    }

    @Test
    void inDoesNotMoveAnAlreadyRecordedCheckIn() {
        Employee e = employee("E1", morning);
        existingLog(e, LocalTime.of(8, 55), null, LogStatus.PRESENT);

        punch(morning, PunchMethod.IN, new PunchEntry("E1", LocalTime.of(11, 0)));

        assertThat(logs.get(e.getId()).getLoggedIn()).isEqualTo(LocalTime.of(8, 55));
        assertThat(statusOf(e)).isEqualTo(LogStatus.PRESENT);
    }

    @Test
    void inOverwritesAnEarlierAbsenceWhenTheEmployeeFinallyArrives() {
        Employee e = employee("E1", morning);
        existingLog(e, null, null, LogStatus.ABSENT); // an earlier IN check already marked them absent

        punch(morning, PunchMethod.IN, new PunchEntry("E1", LocalTime.of(10, 30)));

        assertThat(statusOf(e)).isEqualTo(LogStatus.LATE);
        assertThat(logs.get(e.getId()).getLoggedIn()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void inNeverMarksMissedOut() {
        Employee stillIn = employee("E1", morning);
        existingLog(stillIn, LocalTime.of(9, 0), null, LogStatus.PRESENT);

        punch(morning, PunchMethod.IN); // no punches at all

        assertThat(statusOf(stillIn)).isEqualTo(LogStatus.PRESENT);
    }

    @Test
    void inDoesNotMarkAbsentOnAShiftThatIsNotFollowedUp() {
        WorkMode free = WorkMode.builder()
                .id(UUID.randomUUID()).name("Free").followedUp(false).build();
        when(workModeRepository.findById(free.getId())).thenReturn(Optional.of(free));
        Employee e = employee("E1", free);

        punch(free, PunchMethod.IN);

        assertThat(logs).isEmpty();
    }

    // ---------- method = OUT ----------

    @Test
    void outClosesAnOpenLogAndComputesDuration() {
        Employee e = employee("E1", morning);
        existingLog(e, LocalTime.of(9, 0), null, LogStatus.PRESENT);

        punch(morning, PunchMethod.OUT, new PunchEntry("E1", LocalTime.of(17, 30)));

        EmployeeLog log = logs.get(e.getId());
        assertThat(log.getLoggedOut()).isEqualTo(LocalTime.of(17, 30));
        assertThat(log.getDuration()).isEqualTo(510);
        assertThat(log.getStatus()).isEqualTo(LogStatus.PRESENT); // unchanged
    }

    @Test
    void outMarksMissedOutWhenAnOpenLogHasNoPunch() {
        Employee e = employee("E1", morning);
        existingLog(e, LocalTime.of(9, 0), null, LogStatus.LATE);

        PunchResponse response = punch(morning, PunchMethod.OUT);

        assertThat(statusOf(e)).isEqualTo(LogStatus.MISSED_OUT);
        assertThat(logs.get(e.getId()).getLoggedOut()).isNull();
        assertThat(response.logs()).extracting(EmployeeLogResponse::status).containsExactly(LogStatus.MISSED_OUT);
    }

    @Test
    void outLeavesAnEmployeeWhoNeverLoggedInUntouched() {
        Employee absent = employee("E1", morning);
        existingLog(absent, null, null, LogStatus.ABSENT);
        Employee noLogAtAll = employee("E2", morning);

        punch(morning, PunchMethod.OUT);

        assertThat(statusOf(absent)).isEqualTo(LogStatus.ABSENT); // not MISSED_OUT
        assertThat(logs).doesNotContainKey(noLogAtAll.getId()); // nothing created
    }

    @Test
    void outDoesNotReopenAnAlreadyClosedLog() {
        Employee e = employee("E1", morning);
        existingLog(e, LocalTime.of(9, 0), LocalTime.of(17, 0), LogStatus.PRESENT);

        punch(morning, PunchMethod.OUT, new PunchEntry("E1", LocalTime.of(18, 0)));

        assertThat(logs.get(e.getId()).getLoggedOut()).isEqualTo(LocalTime.of(17, 0));
        assertThat(statusOf(e)).isEqualTo(LogStatus.PRESENT);
    }

    @Test
    void outFlagsMissedOutWhenOnlyPunchEchoesTheCheckIn() {
        Employee e = employee("E1", morning);
        existingLog(e, LocalTime.of(9, 0), null, LogStatus.PRESENT);

        punch(morning, PunchMethod.OUT, new PunchEntry("E1", LocalTime.of(9, 0)));

        assertThat(logs.get(e.getId()).getLoggedOut()).isNull();
        assertThat(statusOf(e)).isEqualTo(LogStatus.MISSED_OUT);
    }

    @Test
    void outPicksThePunchNearestShiftEndWhenEmployeePunchedTwice() {
        Employee e = employee("E1", morning);
        existingLog(e, LocalTime.of(8, 55), null, LogStatus.PRESENT);

        punch(morning, PunchMethod.OUT,
                new PunchEntry("E1", LocalTime.of(8, 55)),  // echo of the check-in
                new PunchEntry("E1", LocalTime.of(17, 5)));  // the real check-out

        EmployeeLog log = logs.get(e.getId());
        assertThat(log.getLoggedOut()).isEqualTo(LocalTime.of(17, 5));
        assertThat(log.getDuration()).isEqualTo(490);
        assertThat(log.getStatus()).isNotEqualTo(LogStatus.MISSED_OUT);
    }

    @Test
    void inPicksTheEarliestPunchWhenEmployeePunchedTwice() {
        Employee e = employee("E1", morning);

        punch(morning, PunchMethod.IN,
                new PunchEntry("E1", LocalTime.of(11, 0)),
                new PunchEntry("E1", LocalTime.of(8, 50)));

        assertThat(logs.get(e.getId()).getLoggedIn()).isEqualTo(LocalTime.of(8, 50));
    }

    @Test
    void outLeavesOpenLogsOnOtherShiftsAlone() {
        Employee onEvening = employee("E9", evening);
        existingLog(onEvening, LocalTime.of(17, 0), null, LogStatus.PRESENT);

        punch(morning, PunchMethod.OUT);

        assertThat(statusOf(onEvening)).isEqualTo(LogStatus.PRESENT); // not MISSED_OUT
    }

    @Test
    void outHandlesAnOvernightShift() {
        Employee e = employee("E1", morning);
        existingLog(e, LocalTime.of(22, 0), null, LogStatus.PRESENT);

        punch(morning, PunchMethod.OUT, new PunchEntry("E1", LocalTime.of(6, 0)));

        assertThat(logs.get(e.getId()).getDuration()).isEqualTo(480);
    }

    // ---------- misc ----------

    @Test
    void unknownShiftIsRejected() {
        UUID unknown = UUID.randomUUID();
        when(workModeRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processPunches(
                DATE, CHECK_TIME, new PunchTarget(unknown, PunchMethod.IN), List.of(), List.of()))
                .isInstanceOf(WorkModeNotFoundException.class);
    }

    @Test
    void aBatchWithNoTargetKeepsTheLegacySweep() {
        Employee onMorning = employee("E1", morning);
        Employee onEvening = employee("E9", evening);
        existingLog(onEvening, LocalTime.of(17, 0), null, LogStatus.PRESENT);

        // No target: every scheduled employee is swept, across shifts.
        service.processPunches(DATE, LocalTime.of(9, 30), null, List.of(),
                List.of(new PunchEntry("E1", LocalTime.of(9, 30))));

        assertThat(statusOf(onMorning)).isEqualTo(LogStatus.LATE);
        assertThat(statusOf(onEvening)).isEqualTo(LogStatus.MISSED_OUT); // legacy cross-shift sweep
    }
}
