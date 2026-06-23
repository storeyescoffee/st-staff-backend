package io.storeyes.accesscontrol.workmodes.dto;

import java.util.UUID;

public record WorkModePairRequest(UUID workModeAId, UUID workModeBId) {}
