package io.storeyes.accesscontrol.schedules.services;

import io.storeyes.accesscontrol.schedules.dto.ScheduleRequest;
import io.storeyes.accesscontrol.schedules.dto.ScheduleResponse;
import io.storeyes.accesscontrol.schedules.entities.Schedule;
import io.storeyes.accesscontrol.schedules.exceptions.ScheduleNotFoundException;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleDetailRepository;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleDetailRepository scheduleDetailRepository;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll() {
        return scheduleRepository.findAll().stream()
                .map(s -> ScheduleResponse.from(s, scheduleDetailRepository.findByScheduleId(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findById(UUID id) {
        Schedule schedule = getOrThrow(id);
        return ScheduleResponse.from(schedule, scheduleDetailRepository.findByScheduleId(id));
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        Schedule schedule = Schedule.builder()
                .numberOfWeek(request.numberOfWeek())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
        Schedule saved = scheduleRepository.save(schedule);
        return ScheduleResponse.from(saved, List.of());
    }

    @Transactional
    public ScheduleResponse update(UUID id, ScheduleRequest request) {
        Schedule schedule = getOrThrow(id);
        schedule.setNumberOfWeek(request.numberOfWeek());
        schedule.setStartDate(request.startDate());
        schedule.setEndDate(request.endDate());
        Schedule saved = scheduleRepository.save(schedule);
        return ScheduleResponse.from(saved, scheduleDetailRepository.findByScheduleId(id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!scheduleRepository.existsById(id)) {
            throw new ScheduleNotFoundException(id);
        }
        scheduleRepository.deleteById(id);
    }

    private Schedule getOrThrow(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ScheduleNotFoundException(id));
    }
}
