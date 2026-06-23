package io.storeyes.accesscontrol.notificationrules.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Single-row table: one boolean column per notification rule. */
@Entity
@Table(name = "notification_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRules {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "late", nullable = false)
    private boolean late;

    @Column(name = "absence", nullable = false)
    private boolean absence;

    @Column(name = "group_alert", nullable = false)
    private boolean groupAlert;

    @Column(name = "dnd", nullable = false)
    private boolean dnd;
}
