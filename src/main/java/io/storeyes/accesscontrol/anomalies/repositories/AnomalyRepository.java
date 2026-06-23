package io.storeyes.accesscontrol.anomalies.repositories;

import io.storeyes.accesscontrol.anomalies.entities.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {

    List<Anomaly> findByEmployeeLog_DateBetween(LocalDate from, LocalDate to);

    boolean existsByEmployeeLog(io.storeyes.accesscontrol.logs.entities.EmployeeLog employeeLog);
}
