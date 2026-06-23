package io.storeyes.accesscontrol.notificationrules.services;

import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleRequest;
import io.storeyes.accesscontrol.notificationrules.dto.NotificationRuleResponse;
import io.storeyes.accesscontrol.notificationrules.entities.NotificationRules;
import io.storeyes.accesscontrol.notificationrules.repositories.NotificationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationRuleService {

    private final NotificationRuleRepository notificationRuleRepository;

    @Transactional(readOnly = true)
    public List<NotificationRuleResponse> findAll() {
        return toResponses(loadRow());
    }

    @Transactional
    public List<NotificationRuleResponse> setEnabled(String ruleId, NotificationRuleRequest req) {
        NotificationRules row = loadRow();
        switch (ruleId) {
            case "late" -> row.setLate(req.enabled());
            case "absence" -> row.setAbsence(req.enabled());
            case "group" -> row.setGroupAlert(req.enabled());
            case "dnd" -> row.setDnd(req.enabled());
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown rule: " + ruleId);
        }
        return toResponses(notificationRuleRepository.save(row));
    }

    private NotificationRules loadRow() {
        return notificationRuleRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification rules not initialized"));
    }

    private List<NotificationRuleResponse> toResponses(NotificationRules r) {
        return List.of(
                new NotificationRuleResponse("late", r.isLate()),
                new NotificationRuleResponse("absence", r.isAbsence()),
                new NotificationRuleResponse("group", r.isGroupAlert()),
                new NotificationRuleResponse("dnd", r.isDnd())
        );
    }
}
