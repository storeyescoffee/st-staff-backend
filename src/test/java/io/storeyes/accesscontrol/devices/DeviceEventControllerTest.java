package io.storeyes.accesscontrol.devices;

import io.storeyes.accesscontrol.devices.config.DeviceIngestProperties;
import io.storeyes.accesscontrol.devices.controllers.DeviceEventController;
import io.storeyes.accesscontrol.devices.parsing.HikvisionEventParser;
import io.storeyes.accesscontrol.devices.services.DeviceEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
}
