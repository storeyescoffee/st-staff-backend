package io.storeyes.accesscontrol.checktimings.entities;

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
@Table(name = "check_timings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CheckTiming {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "time", nullable = false)
    private LocalTime time;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
