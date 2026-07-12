package io.storeyes.accesscontrol.logs.dto;

import java.util.UUID;

/**
 * Narrows a punch batch to a single shift.
 *
 * <p>{@code shiftId} is a {@link io.storeyes.accesscontrol.workmodes.entities.WorkMode} id. Only employees
 * whose schedule for the batch date resolves to that work mode are evaluated; punches for anyone else in
 * the batch are ignored, and no employee outside the shift has their log or status touched.
 *
 * @param shiftId the work mode being checked
 * @param method  {@code IN} to decide PRESENT/LATE/ABSENT, {@code OUT} to decide MISSED_OUT
 */
public record PunchTarget(UUID shiftId, PunchMethod method) {}
