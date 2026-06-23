package io.storeyes.accesscontrol.notificationrules.controllers;

import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleRequest;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleResponse;
import io.storeyes.accesscontrol.notificationrules.services.NotificationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
// The main backend proxies /api/staff/notification-rules -> /api/notification-rules here.
@RequestMapping("/api/notification-rules")
@RequiredArgsConstructor
public class NotificationRuleController {

    private final NotificationRuleService notificationRuleService;

    @GetMapping
    public List<NotificationRuleResponse> list() {
        return notificationRuleService.findAll();
    }

    @PutMapping("/{ruleId}")
    public List<NotificationRuleResponse> update(@PathVariable String ruleId,
                                                 @RequestBody NotificationRuleRequest req) {
        return notificationRuleService.setEnabled(ruleId, req);
    }
}
