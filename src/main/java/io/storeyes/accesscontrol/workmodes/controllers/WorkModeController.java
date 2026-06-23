package io.storeyes.accesscontrol.workmodes.controllers;

import io.storeyes.accesscontrol.workmodes.dto.WorkModeRequest;
import io.storeyes.accesscontrol.workmodes.dto.WorkModeResponse;
import io.storeyes.accesscontrol.workmodes.services.WorkModeService;
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
@RequestMapping("/api/work-modes")
@RequiredArgsConstructor
public class WorkModeController {

    private final WorkModeService workModeService;

    @GetMapping
    public List<WorkModeResponse> list() {
        return workModeService.findAll();
    }

    @GetMapping("/{id}")
    public WorkModeResponse get(@PathVariable UUID id) {
        return workModeService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkModeResponse create(@RequestBody WorkModeRequest request) {
        return workModeService.create(request);
    }

    @PutMapping("/{id}")
    public WorkModeResponse update(@PathVariable UUID id, @RequestBody WorkModeRequest request) {
        return workModeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        workModeService.delete(id);
    }
}
