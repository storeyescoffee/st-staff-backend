package io.storeyes.accesscontrol.logs.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Batch punch request: one timestamp applies to all punches in the list.
 * {@code employees} is optional; any code not already known is created before the punches are processed.
 *
 * <p>The timestamp is an {@link OffsetDateTime} so the device's zone offset (e.g. {@code +01:00}) is
 * accepted. Downstream the offset is dropped, not applied — {@code toLocalDate()/toLocalTime()} keep
 * the device's wall-clock, matching the rest of the naive-local domain model.
 *
 * <p>{@code target} is optional. When present the batch is scoped to one shift and one direction: only
 * employees scheduled on {@code target.shiftId} are evaluated, and only the statuses that direction can
 * produce are written (IN → PRESENT/LATE/ABSENT, OUT → MISSED_OUT). When absent, the legacy untargeted
 * behaviour applies: every scheduled employee is swept for both absence and missing-out.
 */
public record PunchBatchRequest(
        OffsetDateTime timestamp,
        PunchTarget target,
        List<EmployeeUpsert> employees,
        List<PunchEntry> punches) {}
