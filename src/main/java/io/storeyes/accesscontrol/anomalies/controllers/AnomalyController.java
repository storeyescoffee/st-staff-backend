package io.storeyes.accesscontrol.anomalies.controllers;

import io.storeyes.accesscontrol.anomalies.dto.AnomalyPatchRequest;
import io.storeyes.accesscontrol.anomalies.dto.AnomalyResponse;
import io.storeyes.accesscontrol.anomalies.services.AnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService anomalyService;

    /**
     * GET /api/anomalies?from=YYYY-MM-DD&to=YYYY-MM-DD[&employeeId=UUID][&handled=true|false]
     *
     * Omit `handled` to return both open and handled anomalies.
     * Omit `employeeId` to return all employees.
     */
    @GetMapping
    public List<AnomalyResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) Boolean handled) {
        return anomalyService.getAnomalies(from, to, employeeId, handled);
    }

    /**
     * PATCH /api/anomalies/{id}
     * Body: { isHandled?, reason?, description? }
     */
    @PatchMapping("/{id}")
    public AnomalyResponse patch(@PathVariable UUID id, @RequestBody AnomalyPatchRequest req) {
        return anomalyService.patch(id, req);
    }
}
