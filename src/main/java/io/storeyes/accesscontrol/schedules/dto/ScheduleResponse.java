package io.storeyes.accesscontrol.schedules.dto;

import io.storeyes.accesscontrol.schedules.entities.Schedule;
import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        Integer numberOfWeek,
        LocalDate startDate,
        LocalDate endDate,
        List<ScheduleDetailResponse> details) {

    public static ScheduleResponse from(Schedule s, List<ScheduleDetail> details) {
        List<ScheduleDetailResponse> mapped = details.stream()
                .map(ScheduleDetailResponse::from)
                .toList();
        return new ScheduleResponse(
                s.getId(), s.getNumberOfWeek(), s.getStartDate(), s.getEndDate(), mapped);
    }
}
