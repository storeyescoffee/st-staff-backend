package io.storeyes.accesscontrol.devices;

import io.storeyes.accesscontrol.devices.dto.HikvisionEvent;
import io.storeyes.accesscontrol.devices.entities.DeviceEvent;
import io.storeyes.accesscontrol.devices.repositories.DeviceEventRepository;
import io.storeyes.accesscontrol.devices.services.DeviceEventService;
import io.storeyes.accesscontrol.logs.dto.NotificationBatch;
import io.storeyes.accesscontrol.logs.dto.PunchResponse;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.logs.services.EmployeeLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deduplication and delegation for device push events. */
class DeviceEventServiceTest {

    private DeviceEventRepository deviceEventRepository;
    private EmployeeLogService employeeLogService;
    private DeviceEventService service;

    private final HikvisionEvent event = new HikvisionEvent(
            "AccessControllerEvent",
            OffsetDateTime.of(2026, 9, 2, 8, 5, 0, 0, ZoneOffset.ofHours(1)),
            "E001", 5, 38, 1001L, "face", "Alice", "aa:bb:1001");

    @BeforeEach
    void setUp() {
        deviceEventRepository = Mockito.mock(DeviceEventRepository.class);
        employeeLogService = Mockito.mock(EmployeeLogService.class);
        service = new DeviceEventService(deviceEventRepository, employeeLogService);
    }

    @Test
    void processesAFreshEventAndRecordsItInTheLedger() {
        when(deviceEventRepository.existsBySourceKey("aa:bb:1001")).thenReturn(false);

        service.ingest(event);

        verify(employeeLogService).processDeviceEvent(
                LocalDate.of(2026, 9, 2), LocalTime.of(8, 5), "E001", "Alice");

        ArgumentCaptor<DeviceEvent> saved = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(deviceEventRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceKey()).isEqualTo("aa:bb:1001");
        assertThat(saved.getValue().getSerialNo()).isEqualTo(1001L);
        assertThat(saved.getValue().getEmployeeCode()).isEqualTo("E001");
        assertThat(saved.getValue().getSubEventType()).isEqualTo(38);
    }

    @Test
    void skipsAnEventAlreadyInTheLedger() {
        when(deviceEventRepository.existsBySourceKey("aa:bb:1001")).thenReturn(true);

        assertThat(service.ingest(event)).isEmpty();

        verify(employeeLogService, never()).processDeviceEvent(any(), any(), any(), any());
        verify(deviceEventRepository, never()).save(any());
    }

    @Test
    void recordsTheLedgerRowEvenWhenAConcurrentPunchWonTheRace() {
        when(deviceEventRepository.existsBySourceKey("aa:bb:1001")).thenReturn(false);
        when(employeeLogService.processDeviceEvent(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uq_employee_logs_date_emp"));

        service.ingest(event);

        verify(deviceEventRepository).save(any(DeviceEvent.class));
    }

    @Test
    void returnsTheNotificationBatchOfALateCheckIn() {
        NotificationBatch batch = new NotificationBatch(true, false, false, 1, 0, "1 late", List.of(
                new NotificationBatch.Item("E001", "Alice", LogStatus.LATE,
                        LocalTime.of(8, 0), LocalTime.of(8, 5), 5)));
        when(deviceEventRepository.existsBySourceKey("aa:bb:1001")).thenReturn(false);
        when(employeeLogService.processDeviceEvent(any(), any(), any(), any()))
                .thenReturn(new PunchResponse(List.of(), batch));

        assertThat(service.ingest(event)).contains(batch);
    }
}
