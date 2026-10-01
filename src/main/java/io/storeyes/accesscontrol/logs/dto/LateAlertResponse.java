package io.storeyes.accesscontrol.logs.dto;

import java.util.List;

/**
 * Response of POST /api/employee-logs/late-alerts.
 *
 * @param logs          the employees alerted on this run: LATE (checked in late) or ABSENT (not checked in yet)
 * @param notifications what the proxy backend should notify, per the tenant's notification rules
 */
public record LateAlertResponse(List<EmployeeLogResponse> logs, NotificationBatch notifications) {}
