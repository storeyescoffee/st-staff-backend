package io.storeyes.accesscontrol.anomalies.entities;

import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "anomalies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_log_id", nullable = false)
    private EmployeeLog employeeLog;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AnomalyType type;

    /** Set when the anomaly is handled and a reason is provided. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    private AnomalyReason reason;

    /** Free-text justification note, set when handling. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_handled", nullable = false)
    private boolean isHandled;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;
}
