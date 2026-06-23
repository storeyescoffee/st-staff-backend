package io.storeyes.accesscontrol.logs.dto;

/** Single punch entry: the employee's code and the raw time string (HH:mm or HH:mm:ss). */
public record PunchRequest(String employeeCode, String time) {}
