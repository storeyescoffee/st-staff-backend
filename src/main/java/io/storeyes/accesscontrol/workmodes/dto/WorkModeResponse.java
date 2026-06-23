package io.storeyes.accesscontrol.workmodes.dto;

import io.storeyes.accesscontrol.workmodes.entities.WorkMode;

import java.time.LocalTime;
import java.util.UUID;

public record WorkModeResponse(
        UUID id,
        String name,
        String color,
        LocalTime startTime,
        LocalTime endTime,
        Integer tolerantLate,
        boolean isFollowedUp,
        int assignedEmployee) {

    public static WorkModeResponse from(WorkMode m) {
        return new WorkModeResponse(
                m.getId(),
                m.getName(),
                m.getColor(),
                m.getStartTime(),
                m.getEndTime(),
                m.getTolerantLate(),
                m.isFollowedUp(),
                m.getAssignedEmployee());
    }
}
