package io.storeyes.accesscontrol.devices.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotency ledger for device push notifications: one row per event we accepted and processed.
 *
 * <p>Hikvision terminals re-push their event history on reconnect and re-push any event we answer
 * with a non-2xx, so the receiver checks {@code source_key} before processing and returns 200
 * regardless. See {@link io.storeyes.accesscontrol.devices.services.DeviceEventService}.
 */
@Entity
@Table(
        name = "device_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_events_source_key",
                columnNames = "source_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Device id + event serial, or a time/person hash when the device sends no serial. */
    @Column(name = "source_key", nullable = false, length = 200)
    private String sourceKey;

    @Column(name = "serial_no")
    private Long serialNo;

    @Column(name = "employee_code")
    private String employeeCode;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "major_event_type")
    private Integer majorEventType;

    @Column(name = "sub_event_type")
    private Integer subEventType;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false, nullable = false)
    private LocalDateTime receivedAt;
}
