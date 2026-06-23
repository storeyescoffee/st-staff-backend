package io.storeyes.accesscontrol.schedules.dto;

import java.time.DayOfWeek;
import java.util.UUID;

/** {@code workModeId} may be null (off / rest). */
public record ScheduleDetailRequest(
        UUID scheduleId,
        UUID employeeId,
        DayOfWeek dow,
        UUID workModeId) {
}
