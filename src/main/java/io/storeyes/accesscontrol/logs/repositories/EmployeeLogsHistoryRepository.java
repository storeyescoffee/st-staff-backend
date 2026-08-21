package io.storeyes.accesscontrol.logs.repositories;

import io.storeyes.accesscontrol.logs.entities.EmployeeLogsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeLogsHistoryRepository extends JpaRepository<EmployeeLogsHistory, UUID> {
    List<EmployeeLogsHistory> findByDateOrderByCreatedAtDesc(LocalDate date);
}
