package io.storeyes.accesscontrol.checktimings.dto;

import io.storeyes.accesscontrol.checktimings.entities.CheckTiming;

import java.util.UUID;

public record CheckTimingResponse(UUID id, String time, boolean isActive) {

    public static CheckTimingResponse from(CheckTiming ct) {
        return new CheckTimingResponse(ct.getId(), ct.getTime().toString(), ct.isActive());
    }
}
