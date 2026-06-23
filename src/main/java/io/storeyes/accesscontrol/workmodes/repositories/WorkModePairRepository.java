package io.storeyes.accesscontrol.workmodes.repositories;

import io.storeyes.accesscontrol.workmodes.entities.WorkModePair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WorkModePairRepository extends JpaRepository<WorkModePair, UUID> {}
