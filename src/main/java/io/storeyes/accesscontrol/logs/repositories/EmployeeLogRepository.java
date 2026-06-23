package io.storeyes.accesscontrol.logs.repositories;

import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeLogRepository extends JpaRepository<EmployeeLog, UUID> {

    List<EmployeeLog> findByDate(LocalDate date);

    java.util.Optional<EmployeeLog> findByDateAndEmployee_Id(LocalDate date, UUID employeeId);

    List<EmployeeLog> findByDateBetween(LocalDate from, LocalDate to);

    List<EmployeeLog> findByDateBetweenAndEmployee_Id(LocalDate from, LocalDate to, UUID employeeId);
}
