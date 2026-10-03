package io.storeyes.accesscontrol.workmodes.services;

import io.storeyes.accesscontrol.workmodes.dto.WorkModeRequest;
import io.storeyes.accesscontrol.workmodes.dto.WorkModeResponse;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import io.storeyes.accesscontrol.workmodes.exceptions.WorkModeNotFoundException;
import io.storeyes.accesscontrol.workmodes.repositories.WorkModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkModeService {

    private final WorkModeRepository workModeRepository;

    @Transactional(readOnly = true)
    public List<WorkModeResponse> findAll() {
        return workModeRepository.findAll().stream()
                .map(WorkModeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkModeResponse findById(UUID id) {
        return WorkModeResponse.from(getOrThrow(id));
    }

    @Transactional
    public WorkModeResponse create(WorkModeRequest request) {
        WorkMode mode = WorkMode.builder()
                .name(request.name())
                .color(request.color())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .tolerantLate(request.tolerantLate())
                .tolerantOut(request.tolerantOut())
                .timeToNotify(validTimeToNotify(request.timeToNotify()))
                .followedUp(Boolean.TRUE.equals(request.isFollowedUp()))
                .assignedEmployee(0)
                .build();
        return WorkModeResponse.from(workModeRepository.save(mode));
    }

    @Transactional
    public WorkModeResponse update(UUID id, WorkModeRequest request) {
        WorkMode mode = getOrThrow(id);
        mode.setName(request.name());
        mode.setColor(request.color());
        mode.setStartTime(request.startTime());
        mode.setEndTime(request.endTime());
        mode.setTolerantLate(request.tolerantLate());
        mode.setTolerantOut(request.tolerantOut());
        mode.setTimeToNotify(validTimeToNotify(request.timeToNotify()));
        if (request.isFollowedUp() != null) {
            mode.setFollowedUp(request.isFollowedUp());
        }
        return WorkModeResponse.from(workModeRepository.save(mode));
    }

    @Transactional
    public void delete(UUID id) {
        if (!workModeRepository.existsById(id)) {
            throw new WorkModeNotFoundException(id);
        }
        workModeRepository.deleteById(id);
    }

    private static final Set<Integer> TIME_TO_NOTIFY_VALUES = Set.of(15, 30, 45, 60);

    private static Integer validTimeToNotify(Integer value) {
        if (value != null && !TIME_TO_NOTIFY_VALUES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeToNotify must be one of 15, 30, 45, 60");
        }
        return value;
    }

    private WorkMode getOrThrow(UUID id) {
        return workModeRepository.findById(id)
                .orElseThrow(() -> new WorkModeNotFoundException(id));
    }
}
