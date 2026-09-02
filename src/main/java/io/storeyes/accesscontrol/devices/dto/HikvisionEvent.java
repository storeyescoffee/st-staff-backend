package io.storeyes.accesscontrol.devices.dto;

import java.time.OffsetDateTime;

/**
 * One access-control verification event as pushed by a Hikvision terminal (HTTP host notification),
 * normalized to the fields we act on. Produced by
 * {@link io.storeyes.accesscontrol.devices.parsing.HikvisionEventParser} after the noise filter,
 * so an instance always carries an {@link #employeeCode()} and is worth processing.
 *
 * <p>Field names mirror the device payload
 * ({@code eventType}, {@code dateTime}, {@code AccessControllerEvent.{majorEventType, subEventType,
 * employeeNoString, name, currentVerifyMode, serialNo}}) — see the reference receiver at
 * {@code Storeyes-Projects/hikvision-webhooks/main.py:64-129}.
 *
 * @param eventType   e.g. {@code AccessControllerEvent}
 * @param dateTime    device wall-clock with its zone offset; the offset is dropped downstream,
 *                    matching the naive-local domain model (see {@code PunchBatchRequest})
 * @param employeeCode the device's {@code employeeNoString}; matched against {@code Employee.code}
 * @param majorEventType raw Hikvision major type (kept for auditing / dedup)
 * @param subEventType   raw Hikvision sub type (kept for auditing / dedup)
 * @param serialNo    per-device incrementing event serial, when present
 * @param verifyMode  e.g. {@code face}, {@code card}, {@code fingerprint}
 * @param personName  device-reported name, if any
 * @param sourceKey   idempotency key for the dedup ledger (device id + serial, or a time/person hash)
 */
public record HikvisionEvent(
        String eventType,
        OffsetDateTime dateTime,
        String employeeCode,
        Integer majorEventType,
        Integer subEventType,
        Long serialNo,
        String verifyMode,
        String personName,
        String sourceKey) {}
