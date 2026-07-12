package io.storeyes.accesscontrol.logs.dto;

/** Which side of the shift a targeted punch batch is checking. */
public enum PunchMethod {
    /** Clock-in check: decides PRESENT / LATE / ABSENT. */
    IN,
    /** Clock-out check: closes open logs and decides MISSED_OUT. */
    OUT
}
