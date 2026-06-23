package io.storeyes.accesscontrol.anomalies.dto;

import io.storeyes.accesscontrol.anomalies.entities.AnomalyReason;

public record AnomalyPatchRequest(
        /** Mark the anomaly as handled (or re-open with false). */
        Boolean isHandled,
        /** Reason chosen from the predefined list; null = no reason provided. */
        AnomalyReason reason,
        /** Free-text justification note. */
        String description
) {}
