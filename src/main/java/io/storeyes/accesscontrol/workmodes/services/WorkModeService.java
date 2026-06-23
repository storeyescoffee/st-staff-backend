package io.storeyes.accesscontrol.workmodes.services;

import io.storeyes.accesscontrol.workmodes.dto.WorkModeRequest;
import io.storeyes.accesscontrol.workmodes.dto.WorkModeResponse;
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

    private WorkMode getOrThrow(UUID id) {
        return workModeRepository.findById(id)
                .orElseThrow(() -> new WorkModeNotFoundException(id));
    }
}
