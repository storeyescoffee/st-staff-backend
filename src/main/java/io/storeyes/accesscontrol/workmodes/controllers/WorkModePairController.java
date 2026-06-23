package io.storeyes.accesscontrol.workmodes.controllers;

import io.storeyes.accesscontrol.workmodes.dto.WorkModePairRequest;
import io.storeyes.accesscontrol.workmodes.dto.WorkModePairResponse;
import io.storeyes.accesscontrol.workmodes.services.WorkModePairService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/work-mode-pairs")
@RequiredArgsConstructor
public class WorkModePairController {

    private final WorkModePairService workModePairService;

    @GetMapping
    public List<WorkModePairResponse> list() {
        return workModePairService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkModePairResponse create(@RequestBody WorkModePairRequest request) {
        return workModePairService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        workModePairService.delete(id);
    }
}
