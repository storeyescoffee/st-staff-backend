package io.storeyes.accesscontrol.logs;

import io.storeyes.accesscontrol.anomalies.repositories.AnomalyRepository;
import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.employees.repositories.EmployeeRepository;
import io.storeyes.accesscontrol.logs.dto.EmployeeLogResponse;
import io.storeyes.accesscontrol.logs.dto.LateAlertResponse;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogRepository;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogsHistoryRepository;
import io.storeyes.accesscontrol.logs.repositories.ShiftAlertRepository;
import io.storeyes.accesscontrol.logs.services.EmployeeLogService;
import io.storeyes.accesscontrol.logs.services.LateAlertService;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleResponse;
import io.storeyes.accesscontrol.notificationrules.services.NotificationRuleService;
import io.storeyes.accesscontrol.schedules.entities.Schedule;
import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleDetailRepository;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleRepository;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import io.storeyes.accesscontrol.workmodes.repositories.WorkModeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Half-hourly late-arrival alerts: a shift is due while {@code start + tolerance + 30 <= now < start +
 * tolerance + 60}, and each employee's shift is alerted at most once.
 */
class LateAlertServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 12);

    /** 09:00 start, 15 min tolerance → cutoff 09:15, alert window [09:45, 10:15). */
    private final WorkMode morning = WorkMode.builder()
            .id(UUID.randomUUID()).name("Morning")
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0))
            .tolerantLate(15).followedUp(true)
            .build();

    private NotificationRuleService notificationRuleService;
    private LateAlertService service;

    private final List<Employee> roster = new ArrayList<>();
    private final Map<UUID, WorkMode> scheduled = new HashMap<>();
    private final Map<UUID, EmployeeLog> logs = new HashMap<>();
    /** The shift_alerts ledger: (date, employee, shift) keys already claimed. */
    private final Set<List<Object>> claimed = new HashSet<>();

    @BeforeEach
    void setUp() {
        EmployeeLogRepository employeeLogRepository = Mockito.mock(EmployeeLogRepository.class);
        EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
        ScheduleRepository scheduleRepository = Mockito.mock(ScheduleRepository.class);
        ScheduleDetailRepository scheduleDetailRepository = Mockito.mock(ScheduleDetailRepository.class);
        ShiftAlertRepository shiftAlertRepository = Mockito.mock(ShiftAlertRepository.class);
        notificationRuleService = Mockito.mock(NotificationRuleService.class);

        EmployeeLogService employeeLogService = new EmployeeLogService(
                employeeLogRepository, employeeRepository, scheduleRepository, scheduleDetailRepository,
                Mockito.mock(AnomalyRepository.class), notificationRuleService,
                Mockito.mock(WorkModeRepository.class), Mockito.mock(EmployeeLogsHistoryRepository.class),
                new ObjectMapper());
        service = new LateAlertService(employeeLogService, employeeLogRepository, employeeRepository,
                shiftAlertRepository, notificationRuleService);

        rules(true, true);
        when(employeeRepository.findAllByDeletedFalse()).thenReturn(roster);

        Schedule schedule = Schedule.builder().id(UUID.randomUUID())
                .startDate(DATE.minusDays(7)).endDate(DATE.plusDays(7)).build();
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
        when(employeeLogRepository.findByDate(DATE)).thenAnswer(inv -> new ArrayList<>(logs.values()));

        when(shiftAlertRepository.claim(any(), any(), any(), anyString()))
                .thenAnswer(inv -> claimed.add(List.of(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))) ? 1 : 0);
    }

    private void rules(boolean late, boolean absence) {
        when(notificationRuleService.findAll()).thenReturn(List.of(
                new NotificationRuleResponse("late", late),
                new NotificationRuleResponse("absence", absence)));
    }

    private Employee employee(String code) {
        Employee e = Employee.builder().id(UUID.randomUUID()).name("Name " + code).code(code).build();
        roster.add(e);
        scheduled.put(e.getId(), morning);
        return e;
    }

    private void checkedIn(Employee e, LocalTime in, LogStatus status) {
        logs.put(e.getId(), EmployeeLog.builder()
                .id(UUID.randomUUID()).date(DATE).employee(e).workMode(morning)
                .loggedIn(in).status(status)
                .build());
    }

    private LateAlertResponse runAt(int hour, int minute) {
        return service.claimDueAlerts(LocalDateTime.of(DATE, LocalTime.of(hour, minute)));
    }

    private static List<String> codes(LateAlertResponse r) {
        return r.logs().stream().map(l -> l.employee().code() + ":" + l.status()).toList();
    }

    @Test
    void alertsLateAndNotArrivedButNotPresentInsideTheWindow() {
        checkedIn(employee("LATE"), LocalTime.of(9, 30), LogStatus.LATE);
        checkedIn(employee("ONTIME"), LocalTime.of(8, 55), LogStatus.PRESENT);
        employee("NOSHOW");

        LateAlertResponse r = runAt(10, 0);

        assertThat(codes(r)).containsExactlyInAnyOrder("LATE:LATE", "NOSHOW:ABSENT");
        assertThat(r.notifications().send()).isTrue();
        assertThat(r.notifications().lateCount()).isEqualTo(1);
        assertThat(r.notifications().absenceCount()).isEqualTo(1);
        assertThat(r.notifications().items())
                .filteredOn(i -> i.status() == LogStatus.LATE)
                .singleElement()
                .satisfies(i -> assertThat(i.minutesLate()).isEqualTo(30));
    }

    @Test
    void windowIsCutoffPlus30InclusiveToCutoffPlus60Exclusive() {
        employee("NOSHOW");

        assertThat(runAt(9, 44).logs()).isEmpty();
        assertThat(runAt(10, 15).logs()).isEmpty();
        assertThat(codes(runAt(9, 45))).containsExactly("NOSHOW:ABSENT");
    }

    @Test
    void sameShiftIsAlertedOnlyOnce() {
        employee("NOSHOW");

        assertThat(runAt(9, 50).logs()).hasSize(1);
        LateAlertResponse again = runAt(10, 0);

        assertThat(again.logs()).isEmpty();
        assertThat(again.notifications().send()).isFalse();
    }

    @Test
    void disabledRulesNeitherAlertNorClaim() {
        checkedIn(employee("LATE"), LocalTime.of(9, 30), LogStatus.LATE);
        employee("NOSHOW");
        rules(true, false);

        assertThat(codes(runAt(10, 0))).containsExactly("LATE:LATE");

        // Turning absence on later in the same window still alerts the no-show: it was never claimed.
        rules(true, true);
        assertThat(codes(runAt(10, 5))).containsExactly("NOSHOW:ABSENT");
    }

    @Test
    void untrackedShiftsAreIgnored() {
        Employee e = employee("FREE");
        scheduled.put(e.getId(), WorkMode.builder().id(UUID.randomUUID()).name("Free")
                .startTime(LocalTime.of(9, 0)).tolerantLate(15).followedUp(false).build());

        assertThat(runAt(10, 0).logs()).isEmpty();
    }

    @Test
    void responseLogsAreAttendanceRows() {
        employee("NOSHOW");

        EmployeeLogResponse row = runAt(10, 0).logs().getFirst();

        assertThat(row.date()).isEqualTo(DATE);
        assertThat(row.workMode().startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(row.loggedIn()).isNull();
    }
}
