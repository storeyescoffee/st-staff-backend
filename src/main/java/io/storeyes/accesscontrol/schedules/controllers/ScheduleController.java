package io.storeyes.accesscontrol.schedules.controllers;

import io.storeyes.accesscontrol.schedules.dto.ScheduleDetailResponse;
import io.storeyes.accesscontrol.schedules.dto.ScheduleRequest;
import io.storeyes.accesscontrol.schedules.dto.ScheduleResponse;
import io.storeyes.accesscontrol.schedules.services.ScheduleDetailService;
import io.storeyes.accesscontrol.schedules.services.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleDetailService scheduleDetailService;

    @GetMapping
    public List<ScheduleResponse> list() {
        return scheduleService.findAll();
    }

    @GetMapping("/{id}")
    public ScheduleResponse get(@PathVariable UUID id) {
        return scheduleService.findById(id);
    }

    @GetMapping("/{id}/details")
    public List<ScheduleDetailResponse> details(@PathVariable UUID id) {
        return scheduleDetailService.findBySchedule(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@RequestBody ScheduleRequest request) {
        return scheduleService.create(request);
    }

    @PutMapping("/{id}")
    public ScheduleResponse update(@PathVariable UUID id, @RequestBody ScheduleRequest request) {
        return scheduleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        scheduleService.delete(id);
    }
}
