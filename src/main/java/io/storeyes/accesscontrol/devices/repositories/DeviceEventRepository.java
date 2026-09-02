package io.storeyes.accesscontrol.devices.repositories;

import io.storeyes.accesscontrol.devices.entities.DeviceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeviceEventRepository extends JpaRepository<DeviceEvent, UUID> {

    boolean existsBySourceKey(String sourceKey);
}
