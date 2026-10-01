package io.storeyes.accesscontrol.logs.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A late-arrival alert already sent for one employee's shift on one day; rows are claimed via native insert. */
@Entity
@Table(name = "shift_alerts")
@Getter
@NoArgsConstructor
public class ShiftAlert {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_mode_id", nullable = false)
    private UUID workModeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LogStatus status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
