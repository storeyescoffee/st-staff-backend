package io.storeyes.accesscontrol.schedules.repositories;

import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleDetailRepository extends JpaRepository<ScheduleDetail, UUID> {

    List<ScheduleDetail> findByScheduleId(UUID scheduleId);

    List<ScheduleDetail> findByScheduleIdAndDow(UUID scheduleId, DayOfWeek dow);
}
