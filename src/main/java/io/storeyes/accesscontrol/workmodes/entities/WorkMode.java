package io.storeyes.accesscontrol.workmodes.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "work_modes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkMode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    /** Hex colour used for the mode pill in the UI (e.g. {@code #1B2B6B}). */
    @Column(name = "color")
    private String color;

    /** Expected clock-in time; null for free / off modes. */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** Expected clock-out time; null for free / off modes. */
    @Column(name = "end_time")
    private LocalTime endTime;

    /** Allowed lateness in minutes; null when lateness is not tracked. */
    @Column(name = "tolerant_late")
    private Integer tolerantLate;

    @Column(name = "followed_up", nullable = false)
    private boolean followedUp;

    /** Denormalised count of employees on this mode, kept in sync by EmployeeService. */
    @Column(name = "assigned_employee", nullable = false)
    @Builder.Default
    private int assignedEmployee = 0;
}
