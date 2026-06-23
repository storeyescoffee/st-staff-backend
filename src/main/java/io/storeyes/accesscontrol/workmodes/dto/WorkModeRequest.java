package io.storeyes.accesscontrol.workmodes.dto;

import java.time.LocalTime;

/**
 * Payload for creating or updating a work mode.
 * {@code assignedEmployee} is not settable here — it is maintained automatically
 * as employees are assigned to or removed from the mode.
 */
public record WorkModeRequest(
        String name,
        String color,
        LocalTime startTime,
        LocalTime endTime,
        Integer tolerantLate,
        Boolean isFollowedUp) {
}
