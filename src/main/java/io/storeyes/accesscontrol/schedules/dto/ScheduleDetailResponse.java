package io.storeyes.accesscontrol.schedules.dto;

import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;

import java.time.DayOfWeek;
import java.util.UUID;

public record ScheduleDetailResponse(
        UUID id,
        UUID scheduleId,
        EmployeeSummary employee,
        DayOfWeek dow,
        WorkModeSummary workMode) {

    public record EmployeeSummary(UUID id, String name) {
    }

    public record WorkModeSummary(UUID id, String name, String color) {
    }

    public static ScheduleDetailResponse from(ScheduleDetail d) {
        EmployeeSummary employee = d.getEmployee() == null
                ? null
                : new EmployeeSummary(d.getEmployee().getId(), d.getEmployee().getName());
        WorkMode wm = d.getWorkMode();
        WorkModeSummary workMode = wm == null
                ? null
                : new WorkModeSummary(wm.getId(), wm.getName(), wm.getColor());
        return new ScheduleDetailResponse(
                d.getId(), d.getSchedule().getId(), employee, d.getDow(), workMode);
    }
}
