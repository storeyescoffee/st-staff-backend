package io.storeyes.accesscontrol.notificationrules;

import io.storeyes.accesscontrol.notificationrules.controllers.NotificationRuleController;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleRequest;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleResponse;
import io.storeyes.accesscontrol.notificationrules.services.NotificationRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test (no Spring context / DB) verifying the controller routes
 * resolve at /api/notification-rules (the main backend proxies /api/staff/* here) and
 * return the expected JSON.
 */
class NotificationRuleControllerTest {

    private NotificationRuleService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(NotificationRuleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationRuleController(service)).build();
    }

    private List<NotificationRuleResponse> sampleRules() {
        return List.of(
                new NotificationRuleResponse("late", true),
                new NotificationRuleResponse("absence", true),
                new NotificationRuleResponse("group", false),
                new NotificationRuleResponse("dnd", true)
        );
    }

    @Test
    void getReturnsAllRules() throws Exception {
        when(service.findAll()).thenReturn(sampleRules());

        mockMvc.perform(get("/api/notification-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value("late"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[2].id").value("group"))
                .andExpect(jsonPath("$[2].enabled").value(false));
    }

    @Test
    void putTogglesRule() throws Exception {
        when(service.setEnabled(eq("group"), any(NotificationRuleRequest.class)))
                .thenReturn(sampleRules());

        mockMvc.perform(put("/api/notification-rules/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        verify(service).setEnabled(eq("group"), any(NotificationRuleRequest.class));
    }
}
