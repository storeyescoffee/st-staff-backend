package io.storeyes.accesscontrol.workmodes.repositories;

import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WorkModeRepository extends JpaRepository<WorkMode, UUID> {
}
