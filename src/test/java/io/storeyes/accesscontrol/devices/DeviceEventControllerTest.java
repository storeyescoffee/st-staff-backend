package io.storeyes.accesscontrol.devices;

import io.storeyes.accesscontrol.devices.config.DeviceIngestProperties;
import io.storeyes.accesscontrol.devices.controllers.DeviceEventController;
import io.storeyes.accesscontrol.devices.parsing.HikvisionEventParser;
import io.storeyes.accesscontrol.devices.services.DeviceEventService;
import io.storeyes.accesscontrol.logs.dto.NotificationBatch;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc test (no Spring context / DB) for the device push endpoint. */
class DeviceEventControllerTest {

    private static final String SUCCESS_EVENT = """
            {"eventType":"AccessControllerEvent","dateTime":"2026-09-02T08:05:00+01:00",
             "macAddress":"aa:bb","AccessControllerEvent":{"majorEventType":5,"subEventType":38,
               "employeeNoString":"E001","serialNo":1001}}
            """;

    private static final String DOOR_STATUS_EVENT = """
            {"eventType":"AccessControllerEvent","dateTime":"2026-09-02T08:05:00+01:00",
             "AccessControllerEvent":{"majorEventType":3,"subEventType":21}}
            """;

    private DeviceEventService deviceEventService;

    private MockMvc mockMvc(DeviceIngestProperties properties) {
        deviceEventService = Mockito.mock(DeviceEventService.class);
        DeviceEventController controller = new DeviceEventController(
                new HikvisionEventParser(new ObjectMapper()), deviceEventService, properties);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new DeviceIngestProperties("lendys", true, null));
    }

    @Test
    void healthCheckReportsListening() throws Exception {
        mockMvc.perform(get("/api/device-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("listening"));
    }

    @Test
    void acceptsAMultipartEventLogPush() throws Exception {
        mockMvc.perform(multipart("/api/device-events").param("event_log", SUCCESS_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(deviceEventService).ingest(any());
    }

    @Test
    void acceptsARawMultipartBodyWeParseOurselves() throws Exception {
        String boundary = "boundaryABC";
        String raw = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"event_log\"\r\n\r\n"
                + SUCCESS_EVENT + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"Picture\"; filename=\"snap.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n"
                + "ÿØÿàignored-bytes\r\n"
                + "--" + boundary + "--\r\n";

        mockMvc.perform(post("/api/device-events")
                        .contentType("multipart/form-data; boundary=" + boundary)
                        .content(raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(deviceEventService).ingest(any());
    }

    @Test
    void stillFindsTheEventPartWhenTheClosingBoundaryIsMissing() throws Exception {
        String boundary = "boundaryABC";
        String truncated = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"event_log\"\r\n\r\n"
                + SUCCESS_EVENT; // device dropped the connection before the closing boundary

        mockMvc.perform(post("/api/device-events")
                        .contentType("multipart/form-data; boundary=" + boundary)
                        .content(truncated.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(deviceEventService).ingest(any());
    }

    @Test
    void acceptsABareJsonBody() throws Exception {
        mockMvc.perform(post("/api/device-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(deviceEventService).ingest(any());
    }

    @Test
    void acknowledgesButDoesNotProcessNoiseEvents() throws Exception {
        mockMvc.perform(post("/api/device-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOOR_STATUS_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));

        verify(deviceEventService, never()).ingest(any());
    }

    @Test
    void rejectsARequestWithNoEventData() throws Exception {
        mockMvc.perform(post("/api/device-events").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void rejectsAPushMissingTheSharedSecret() throws Exception {
        MockMvc secured = mockMvc(new DeviceIngestProperties("lendys", true, "s3cret"));

        secured.perform(post("/api/device-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_EVENT))
                .andExpect(status().isUnauthorized());

        verify(deviceEventService, never()).ingest(any());
    }

    @Test
    void acceptsAPushCarryingTheSharedSecret() throws Exception {
        MockMvc secured = mockMvc(new DeviceIngestProperties("lendys", true, "s3cret"));

        secured.perform(post("/api/device-events")
                        .header("X-Device-Token", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(deviceEventService).ingest(any());
    }

    @Test
    void returnsTheNotificationBatchSoTheProxyCanDispatchIt() throws Exception {
        when(deviceEventService.ingest(any())).thenReturn(Optional.of(new NotificationBatch(
                true, false, false, 1, 0, "1 late", List.of(new NotificationBatch.Item(
                        "E001", "Alice", LogStatus.LATE, LocalTime.of(8, 0), LocalTime.of(8, 5), 5)))));

        mockMvc.perform(post("/api/device-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.notifications.send").value(true))
                .andExpect(jsonPath("$.notifications.items[0].status").value("LATE"))
                .andExpect(jsonPath("$.notifications.items[0].minutesLate").value(5));
    }
}
