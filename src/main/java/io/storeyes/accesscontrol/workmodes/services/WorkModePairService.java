package io.storeyes.accesscontrol.workmodes.services;

import io.storeyes.accesscontrol.workmodes.dto.WorkModePairRequest;
import io.storeyes.accesscontrol.workmodes.dto.WorkModePairResponse;
import io.storeyes.accesscontrol.workmodes.entities.WorkModePair;
import io.storeyes.accesscontrol.workmodes.exceptions.WorkModeNotFoundException;
import io.storeyes.accesscontrol.workmodes.repositories.WorkModePairRepository;
import io.storeyes.accesscontrol.workmodes.repositories.WorkModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkModePairService {

    private final WorkModePairRepository pairRepository;
    private final WorkModeRepository workModeRepository;

    @Transactional(readOnly = true)
    public List<WorkModePairResponse> findAll() {
        return pairRepository.findAll().stream()
                .map(WorkModePairResponse::from)
                .toList();
    }

    @Transactional
    public WorkModePairResponse create(WorkModePairRequest request) {
        var modeA = workModeRepository.findById(request.workModeAId())
                .orElseThrow(() -> new WorkModeNotFoundException(request.workModeAId()));
        var modeB = workModeRepository.findById(request.workModeBId())
                .orElseThrow(() -> new WorkModeNotFoundException(request.workModeBId()));
        var pair = WorkModePair.builder().workModeA(modeA).workModeB(modeB).build();
        return WorkModePairResponse.from(pairRepository.save(pair));
    }

    @Transactional
    public void delete(UUID id) {
        if (!pairRepository.existsById(id)) {
            throw new IllegalArgumentException("Work mode pair not found: " + id);
        }
        pairRepository.deleteById(id);
    }
}
