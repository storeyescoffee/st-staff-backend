package io.storeyes.accesscontrol.checktimings.services;

import io.storeyes.accesscontrol.checktimings.dto.CheckTimingRequest;
import io.storeyes.accesscontrol.checktimings.dto.CheckTimingResponse;
import io.storeyes.accesscontrol.checktimings.entities.CheckTiming;
import io.storeyes.accesscontrol.checktimings.repositories.CheckTimingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckTimingService {

    private final CheckTimingRepository checkTimingRepository;

    @Transactional(readOnly = true)
    public List<CheckTimingResponse> findAll() {
        return checkTimingRepository.findAll().stream()
                .map(CheckTimingResponse::from)
                .toList();
    }

    @Transactional
    public CheckTimingResponse create(CheckTimingRequest req) {
        CheckTiming ct = CheckTiming.builder()
                .time(LocalTime.parse(req.time()))
                .isActive(req.isActive())
                .build();
        return CheckTimingResponse.from(checkTimingRepository.save(ct));
    }

    @Transactional
    public CheckTimingResponse update(UUID id, CheckTimingRequest req) {
        CheckTiming ct = checkTimingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ct.setTime(LocalTime.parse(req.time()));
        ct.setActive(req.isActive());
        return CheckTimingResponse.from(checkTimingRepository.save(ct));
    }

    @Transactional
    public void delete(UUID id) {
        if (!checkTimingRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        checkTimingRepository.deleteById(id);
    }
}
