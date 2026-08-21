package io.storeyes.accesscontrol.logs.entities;

import io.storeyes.accesscontrol.logs.dto.PunchMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Append-only snapshot of one {@code POST /api/employee-logs/punch} call: the shift it targeted (if any),
 * the logs it produced, and the notification batch computed for it.
 */
@Entity
@Table(name = "employee_logs_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeLogsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** Targeted shift's name; null for an untargeted (legacy) batch. */
    @Column(name = "shift_name")
    private String shiftName;

    /** Targeted shift's expected clock-in time; null for an untargeted (legacy) batch. */
    @Column(name = "shift_start_time")
    private LocalTime shiftStartTime;

    /** Targeted shift's expected clock-out time; null for an untargeted (legacy) batch. */
    @Column(name = "shift_end_time")
    private LocalTime shiftEndTime;

    /** Null for an untargeted (legacy) batch, which checks both directions at once. */
    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    private PunchMethod method;

    /** Serialized {@code List<EmployeeLogResponse>} — the logs this batch created or changed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logs", nullable = false, columnDefinition = "jsonb")
    private String logs;

    /** Serialized {@code NotificationBatch}, or null if none was computed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification", columnDefinition = "jsonb")
    private String notification;
}
