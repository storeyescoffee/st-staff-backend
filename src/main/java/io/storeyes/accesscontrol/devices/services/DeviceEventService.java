package io.storeyes.accesscontrol.devices.services;

import io.storeyes.accesscontrol.devices.dto.HikvisionEvent;
import io.storeyes.accesscontrol.devices.entities.DeviceEvent;
import io.storeyes.accesscontrol.devices.repositories.DeviceEventRepository;
import io.storeyes.accesscontrol.logs.dto.NotificationBatch;
import io.storeyes.accesscontrol.logs.dto.PunchResponse;
import io.storeyes.accesscontrol.logs.services.EmployeeLogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Turns one parsed device event into a punch, exactly once.
 *
 * <p>Hikvision terminals re-push their history on reconnect and re-push any event we answer with a
 * non-2xx, so every event is guarded by the {@code device_events} ledger before it reaches the
 * attendance engine. The two DB touches run in their own transactions (no surrounding
 * {@code @Transactional}) so a unique-constraint hit on one does not poison the other.
 *
 * <p>{@link #ingest} hands back the punch's notification batch (e.g. a LATE check-in measured against
 * the employee's planned shift) so the proxy backend can dispatch it; the punch is already committed.
 */
@Service
@RequiredArgsConstructor
public class DeviceEventService {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventService.class);

    private final DeviceEventRepository deviceEventRepository;
    private final EmployeeLogService employeeLogService;

    /**
     * @return the notifications this event produced; empty for a duplicate, a lost race, or an event
     *         that changed nothing
     */
    public Optional<NotificationBatch> ingest(HikvisionEvent event) {
        if (deviceEventRepository.existsBySourceKey(event.sourceKey())) {
            log.debug("Duplicate device event {} — skipping", event.sourceKey());
            return Optional.empty();
        }

        PunchResponse punch = null;
        try {
            punch = employeeLogService.processDeviceEvent(
                    event.dateTime().toLocalDate(),
                    event.dateTime().toLocalTime(),
                    event.employeeCode(),
                    event.personName());
        } catch (DataIntegrityViolationException e) {
            // Concurrent event for the same (date, employee) already created the log — treat as handled.
            log.debug("Concurrent device event {} lost the race — already recorded", event.sourceKey());
        }

        try {
            deviceEventRepository.save(DeviceEvent.builder()
                    .sourceKey(event.sourceKey())
                    .serialNo(event.serialNo())
                    .employeeCode(event.employeeCode())
                    .eventTime(event.dateTime().toLocalDateTime())
                    .majorEventType(event.majorEventType())
                    .subEventType(event.subEventType())
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.debug("Device event {} recorded by another thread first", event.sourceKey());
        }
        return Optional.ofNullable(punch).map(PunchResponse::notifications);
    }
}
