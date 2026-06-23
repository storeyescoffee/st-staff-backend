package io.storeyes.accesscontrol.logs.controllers;

import io.storeyes.accesscontrol.logs.dto.EmployeeLogResponse;
import io.storeyes.accesscontrol.logs.dto.PunchBatchRequest;
import io.storeyes.accesscontrol.logs.dto.PunchResponse;
import io.storeyes.accesscontrol.logs.services.EmployeeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employee-logs")
@RequiredArgsConstructor
public class EmployeeLogController {

    private final EmployeeLogService employeeLogService;

    @GetMapping
    public List<EmployeeLogResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return employeeLogService.getLogsForDate(target);
    }

    @PostMapping("/punch")
    public PunchResponse punch(@RequestBody PunchBatchRequest body) {
        LocalDate date = body.timestamp().toLocalDate();
        return employeeLogService.processPunches(
                date, body.timestamp().toLocalTime(), body.employees(), body.punches());
    }
}
