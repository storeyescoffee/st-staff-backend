package io.storeyes.accesscontrol.logs.entities;

import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "employee_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_employee_logs_date_emp",
                columnNames = {"date", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "work_mode_id")
    private WorkMode workMode;

    @Column(name = "logged_in")
    private LocalTime loggedIn;

    @Column(name = "logged_out")
    private LocalTime loggedOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private LogStatus status;

    /** Work duration in minutes, computed when loggedOut is recorded. */
    @Column(name = "duration")
    private Integer duration;
}
