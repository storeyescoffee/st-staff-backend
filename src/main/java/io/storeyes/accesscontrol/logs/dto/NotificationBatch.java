package io.storeyes.accesscontrol.logs.dto;

import io.storeyes.accesscontrol.logs.entities.LogStatus;

import java.time.LocalTime;
import java.util.List;

/**
 * Notification instructions derived from the punch batch and the tenant's notification rules.
 * The proxy backend consumes this to decide what (if anything) to dispatch.
 *
 * @param send          true if the proxy should dispatch now
 * @param dndSuppressed true if there were notifiable events but they were withheld by the
 *                      Do-Not-Disturb window (proxy may defer them to wake-up)
 * @param grouped       true if the "group" rule is on: send one summary instead of one per employee
 * @param lateCount     number of LATE events in this batch (after the late rule filter)
 * @param absenceCount  number of ABSENT events in this batch (after the absence rule filter)
 * @param summary       human-readable roll-up, e.g. "2 late · 1 absent" (built from the counts)
 * @param items         the individual notifiable events (used when not grouped)
 */
public record NotificationBatch(
        boolean send,
        boolean dndSuppressed,
        boolean grouped,
        int lateCount,
        int absenceCount,
        String summary,
        List<Item> items) {

    /**
     * @param shiftStart  planned start of the employee's shift, if scheduled
     * @param arrivedAt   recorded check-in time, if any
     * @param minutesLate minutes between {@code shiftStart} and {@code arrivedAt}; only set for LATE
     */
    public record Item(
            String employeeCode,
            String employeeName,
            LogStatus status,
            LocalTime shiftStart,
            LocalTime arrivedAt,
            Integer minutesLate) {}
}
