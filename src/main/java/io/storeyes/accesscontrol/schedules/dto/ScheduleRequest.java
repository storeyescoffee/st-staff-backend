package io.storeyes.accesscontrol.schedules.dto;

import java.time.LocalDate;

public record ScheduleRequest(
        Integer numberOfWeek,
        LocalDate startDate,
        LocalDate endDate) {
}
