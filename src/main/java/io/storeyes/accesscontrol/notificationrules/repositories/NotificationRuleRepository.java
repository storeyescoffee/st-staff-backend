package io.storeyes.accesscontrol.notificationrules.repositories;

import io.storeyes.accesscontrol.notificationrules.entities.NotificationRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRuleRepository extends JpaRepository<NotificationRules, UUID> {
}
