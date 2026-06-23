package io.storeyes.accesscontrol.anomalies.dto;

import io.storeyes.accesscontrol.anomalies.entities.Anomaly;
import io.storeyes.accesscontrol.anomalies.entities.AnomalyReason;
import io.storeyes.accesscontrol.anomalies.entities.AnomalyType;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record AnomalyResponse(
        UUID id,
        AnomalyType type,
        AnomalyReason reason,
        String description,
        boolean isHandled,
        LocalDateTime handledAt,

        // employee & log context for card rendering
        LocalDate date,
        UUID employeeId,
        String employeeName,
        String employeeCode,
        String workModeName,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        Integer tolerantLate,
        LocalTime loggedIn,
        LocalTime loggedOut,
        Integer duration
) {
    public static AnomalyResponse from(Anomaly a) {
        EmployeeLog log = a.getEmployeeLog();
        WorkMode wm = log.getWorkMode();
        return new AnomalyResponse(
                a.getId(),
                a.getType(),
                a.getReason(),
                a.getDescription(),
                a.isHandled(),
                a.getHandledAt(),
                log.getDate(),
                log.getEmployee().getId(),
                log.getEmployee().getName(),
                log.getEmployee().getCode(),
                wm != null ? wm.getName() : null,
                wm != null ? wm.getStartTime() : null,
                wm != null ? wm.getEndTime() : null,
                wm != null ? wm.getTolerantLate() : null,
                log.getLoggedIn(),
                log.getLoggedOut(),
                log.getDuration()
        );
    }
}
