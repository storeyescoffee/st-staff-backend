package io.storeyes.accesscontrol.checktimings.repositories;

import io.storeyes.accesscontrol.checktimings.entities.CheckTiming;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CheckTimingRepository extends JpaRepository<CheckTiming, UUID> {
}
