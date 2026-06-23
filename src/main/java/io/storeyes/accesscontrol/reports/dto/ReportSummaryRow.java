package io.storeyes.accesscontrol.reports.dto;

import java.util.UUID;

public record ReportSummaryRow(
        UUID employeeId,
        String employeeName,
        String employeeCode,
        /** Days with at least one punch (loggedIn != null). */
        int daysWorked,
        /** Sum of duration across all logs in the period (minutes). */
        int totalMinutes,
        /** Sum of minutes late across LATE logs (loggedIn - workMode.startTime). */
        int lateTotalMinutes,
        /** Count of LATE logs. */
        int lateDays,
        /** Count of MISSED_OUT logs. */
        int missingOuts,
        /** Count of ABSENT logs. */
        int absences,
        /** Sum of overtime minutes (duration - planned), clamped to 0. */
        int overtimeMinutes
) {}
