package io.storeyes.accesscontrol.checktimings.controllers;

import io.storeyes.accesscontrol.checktimings.dto.CheckTimingRequest;
import io.storeyes.accesscontrol.checktimings.dto.CheckTimingResponse;
import io.storeyes.accesscontrol.checktimings.services.CheckTimingService;
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
@RequestMapping("/api/check-timings")
@RequiredArgsConstructor
public class CheckTimingController {

    private final CheckTimingService checkTimingService;

    @GetMapping
    public List<CheckTimingResponse> list() {
        return checkTimingService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckTimingResponse create(@RequestBody CheckTimingRequest req) {
        return checkTimingService.create(req);
    }

    @PutMapping("/{id}")
    public CheckTimingResponse update(@PathVariable UUID id, @RequestBody CheckTimingRequest req) {
        return checkTimingService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        checkTimingService.delete(id);
    }
}
