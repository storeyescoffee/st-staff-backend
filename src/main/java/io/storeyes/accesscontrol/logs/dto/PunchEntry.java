package io.storeyes.accesscontrol.logs.dto;

import java.time.LocalTime;

/** Single punch entry: each employee carries its own punch time. The batch timestamp supplies only the date. */
public record PunchEntry(String employeeCode, LocalTime time) {}
