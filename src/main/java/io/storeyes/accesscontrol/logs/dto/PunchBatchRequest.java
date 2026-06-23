package io.storeyes.accesscontrol.logs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Batch punch request: one timestamp applies to all punches in the list.
 * {@code employees} is optional; any code not already known is created before the punches are processed.
 */
public record PunchBatchRequest(LocalDateTime timestamp, List<EmployeeUpsert> employees, List<PunchEntry> punches) {}
