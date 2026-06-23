package io.storeyes.accesscontrol.employees.repositories;

import io.storeyes.accesscontrol.employees.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    boolean existsByCode(String code);

    java.util.Optional<io.storeyes.accesscontrol.employees.entities.Employee> findByCode(String code);

    /** Returns, in a single query, the subset of the given codes that already exist. */
    @Query("select e.code from Employee e where e.code in :codes")
    Set<String> findExistingCodes(Collection<String> codes);
}
