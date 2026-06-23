package io.storeyes.accesscontrol.logs.dto;

import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EmployeeLogResponse(
        UUID id,
        LocalDate date,
        EmployeeSummary employee,
        WorkModeSummary workMode,
        LocalTime loggedIn,
        LocalTime loggedOut,
        LogStatus status,
        Integer duration) {

    public record EmployeeSummary(UUID id, String name, String code, String role) {}

    public record WorkModeSummary(UUID id, String name, String color, LocalTime startTime, LocalTime endTime, Integer tolerantLate) {}

    /** Build from an existing log row. */
    public static EmployeeLogResponse from(EmployeeLog log) {
        return new EmployeeLogResponse(
                log.getId(),
                log.getDate(),
                toEmployeeSummary(log.getEmployee()),
                toWorkModeSummary(log.getWorkMode()),
                log.getLoggedIn(),
                log.getLoggedOut(),
                log.getStatus(),
                log.getDuration());
    }

    /** Build a stub for an employee who has no log yet but is scheduled today. */
    public static EmployeeLogResponse stub(LocalDate date, Employee employee, WorkMode workMode) {
        return new EmployeeLogResponse(
                null,
                date,
                toEmployeeSummary(employee),
                toWorkModeSummary(workMode),
                null,
                null,
                null,
                null);
    }

    private static EmployeeSummary toEmployeeSummary(Employee e) {
        String role = (e.getRole() != null) ? e.getRole().getName() : null;
        return new EmployeeSummary(e.getId(), e.getName(), e.getCode(), role);
    }

    private static WorkModeSummary toWorkModeSummary(WorkMode wm) {
        if (wm == null) return null;
        return new WorkModeSummary(wm.getId(), wm.getName(), wm.getColor(), wm.getStartTime(), wm.getEndTime(), wm.getTolerantLate());
    }
}
