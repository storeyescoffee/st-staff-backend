package io.storeyes.accesscontrol.schedules.services;

import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.employees.exceptions.EmployeeNotFoundException;
import io.storeyes.accesscontrol.employees.repositories.EmployeeRepository;
import io.storeyes.accesscontrol.schedules.dto.ScheduleDetailRequest;
import io.storeyes.accesscontrol.schedules.dto.ScheduleDetailResponse;
import io.storeyes.accesscontrol.schedules.entities.Schedule;
import io.storeyes.accesscontrol.schedules.entities.ScheduleDetail;
import io.storeyes.accesscontrol.schedules.exceptions.ScheduleDetailNotFoundException;
import io.storeyes.accesscontrol.schedules.exceptions.ScheduleNotFoundException;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleDetailRepository;
import io.storeyes.accesscontrol.schedules.repositories.ScheduleRepository;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import io.storeyes.accesscontrol.workmodes.exceptions.WorkModeNotFoundException;
import io.storeyes.accesscontrol.workmodes.repositories.WorkModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleDetailService {

    private final ScheduleDetailRepository scheduleDetailRepository;
    private final ScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkModeRepository workModeRepository;

    @Transactional(readOnly = true)
    public List<ScheduleDetailResponse> findBySchedule(UUID scheduleId) {
        return scheduleDetailRepository.findByScheduleId(scheduleId).stream()
                .map(ScheduleDetailResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleDetailResponse create(ScheduleDetailRequest request) {
        ScheduleDetail detail = ScheduleDetail.builder()
                .schedule(resolveSchedule(request.scheduleId()))
                .employee(resolveEmployee(request.employeeId()))
                .dow(request.dow())
                .workMode(resolveWorkMode(request.workModeId()))
                .build();
        return ScheduleDetailResponse.from(scheduleDetailRepository.save(detail));
    }

    @Transactional
    public ScheduleDetailResponse update(UUID id, ScheduleDetailRequest request) {
        ScheduleDetail detail = scheduleDetailRepository.findById(id)
                .orElseThrow(() -> new ScheduleDetailNotFoundException(id));
        detail.setSchedule(resolveSchedule(request.scheduleId()));
        detail.setEmployee(resolveEmployee(request.employeeId()));
        detail.setDow(request.dow());
        detail.setWorkMode(resolveWorkMode(request.workModeId()));
        return ScheduleDetailResponse.from(scheduleDetailRepository.save(detail));
    }

    @Transactional
    public void delete(UUID id) {
        if (!scheduleDetailRepository.existsById(id)) {
            throw new ScheduleDetailNotFoundException(id);
        }
        scheduleDetailRepository.deleteById(id);
    }

    private Schedule resolveSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
    }

    private Employee resolveEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private WorkMode resolveWorkMode(UUID workModeId) {
        if (workModeId == null) {
            return null;
        }
        return workModeRepository.findById(workModeId)
                .orElseThrow(() -> new WorkModeNotFoundException(workModeId));
    }
}
