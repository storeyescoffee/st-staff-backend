package io.storeyes.accesscontrol.notificationrules.dto;

/** Per-rule on/off state, keyed by the rule id shared with the frontend. */
public record NotificationRuleResponse(String id, boolean enabled) {
}
