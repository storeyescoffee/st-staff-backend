package io.storeyes.accesscontrol.employees.repositories;

import io.storeyes.accesscontrol.employees.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByName(String name);
}
