package io.storeyes.accesscontrol.logs.repositories;

import io.storeyes.accesscontrol.logs.entities.ShiftAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface ShiftAlertRepository extends JpaRepository<ShiftAlert, UUID> {

    /**
     * Records the alert unless one already exists for this employee, day and shift.
     *
     * @return 1 when this call claimed the alert (send it), 0 when it was already sent
     */
    @Modifying
    @Query(value = """
            INSERT INTO shift_alerts (date, employee_id, work_mode_id, status)
            VALUES (:date, :employeeId, :workModeId, :status)
            ON CONFLICT (date, employee_id, work_mode_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("date") LocalDate date,
              @Param("employeeId") UUID employeeId,
              @Param("workModeId") UUID workModeId,
              @Param("status") String status);
}
