package io.storeyes.accesscontrol.workmodes.dto;

import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import io.storeyes.accesscontrol.workmodes.entities.WorkModePair;

import java.util.UUID;

public record WorkModePairResponse(UUID id, ModeSummary workModeA, ModeSummary workModeB) {

    public record ModeSummary(UUID id, String name, String color) {
        static ModeSummary from(WorkMode m) {
            return new ModeSummary(m.getId(), m.getName(), m.getColor());
        }
    }

    public static WorkModePairResponse from(WorkModePair pair) {
        return new WorkModePairResponse(
                pair.getId(),
                ModeSummary.from(pair.getWorkModeA()),
                ModeSummary.from(pair.getWorkModeB()));
    }
}
