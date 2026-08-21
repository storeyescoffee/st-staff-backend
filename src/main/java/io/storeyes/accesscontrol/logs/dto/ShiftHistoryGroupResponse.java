package io.storeyes.accesscontrol.logs.dto;

import tools.jackson.databind.JsonNode;

import java.time.LocalTime;

/**
 * One shift's punch history for a date: the latest {@code IN} batch and the latest {@code OUT} batch
 * recorded for it, each null if that direction hasn't been punched yet.
 */
public record ShiftHistoryGroupResponse(ShiftInfo shift, Direction in, Direction out) {

    public record ShiftInfo(String name, LocalTime startTime, LocalTime endTime) {}

    public record Direction(JsonNode logs, JsonNode notification) {}
}
