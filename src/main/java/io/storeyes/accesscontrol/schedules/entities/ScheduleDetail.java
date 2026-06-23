package io.storeyes.accesscontrol.schedules.entities;

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

import java.time.DayOfWeek;
import java.util.UUID;

@Entity
@Table(
        name = "schedule_details",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_schedule_details_cell",
                columnNames = {"schedule_id", "employee_id", "dow"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "dow", nullable = false)
    private DayOfWeek dow;

    /** Assigned work mode; null means off / rest. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "work_mode_id")
    private WorkMode workMode;
}
