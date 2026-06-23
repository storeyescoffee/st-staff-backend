package io.storeyes.accesscontrol.schedules.controllers;

import io.storeyes.accesscontrol.schedules.dto.ScheduleDetailRequest;
import io.storeyes.accesscontrol.schedules.dto.ScheduleDetailResponse;
import io.storeyes.accesscontrol.schedules.services.ScheduleDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/schedule-details")
@RequiredArgsConstructor
public class ScheduleDetailController {

    private final ScheduleDetailService scheduleDetailService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleDetailResponse create(@RequestBody ScheduleDetailRequest request) {
        return scheduleDetailService.create(request);
    }

    @PutMapping("/{id}")
    public ScheduleDetailResponse update(
            @PathVariable UUID id, @RequestBody ScheduleDetailRequest request) {
        return scheduleDetailService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        scheduleDetailService.delete(id);
    }
}
