package io.storeyes.accesscontrol.logs.dto;

import java.util.List;

/**
 * Response of POST /api/employee-logs/punch.
 *
 * @param logs          the logs created or changed by this batch (same data as before)
 * @param notifications what the proxy backend should notify, per the tenant's notification rules
 */
public record PunchResponse(List<EmployeeLogResponse> logs, NotificationBatch notifications) {}
