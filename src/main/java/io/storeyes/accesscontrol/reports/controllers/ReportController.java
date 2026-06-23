package io.storeyes.accesscontrol.reports.controllers;

import io.storeyes.accesscontrol.reports.dto.ReportSummaryRow;
import io.storeyes.accesscontrol.reports.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /api/reports/summary?from=2026-06-01&to=2026-06-30[&employeeId=UUID]
     * Returns one aggregated row per employee for the given date range.
     */
    @GetMapping("/summary")
    public List<ReportSummaryRow> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID employeeId) {
        return reportService.getSummary(from, to, employeeId);
    }
}
