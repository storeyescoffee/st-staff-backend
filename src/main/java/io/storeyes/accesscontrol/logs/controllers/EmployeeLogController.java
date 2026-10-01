package io.storeyes.accesscontrol.logs.controllers;

import io.storeyes.accesscontrol.logs.dto.EmployeeLogResponse;
import io.storeyes.accesscontrol.logs.dto.LateAlertResponse;
import io.storeyes.accesscontrol.logs.dto.PunchBatchRequest;
import io.storeyes.accesscontrol.logs.dto.PunchResponse;
import io.storeyes.accesscontrol.logs.dto.ShiftHistoryGroupResponse;
import io.storeyes.accesscontrol.logs.services.EmployeeLogService;
import io.storeyes.accesscontrol.logs.services.LateAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/employee-logs")
@RequiredArgsConstructor
public class EmployeeLogController {

    private final EmployeeLogService employeeLogService;
    private final LateAlertService lateAlertService;

    /** Zone the shifts' planned times are expressed in; the alert window is evaluated against its clock. */
    @Value("${late-alerts.zone:Africa/Casablanca}")
    private String lateAlertsZone;

    @GetMapping
    public List<EmployeeLogResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return employeeLogService.getLogsForDate(target);
    }

    /** History is scoped to the requesting store via the X-STORE-CODE header, same as every other endpoint. */
    @GetMapping("/history")
    public List<ShiftHistoryGroupResponse> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return employeeLogService.getHistoryForDate(target);
    }

    @PostMapping("/punch")
    public PunchResponse punch(@RequestBody PunchBatchRequest body) {
        LocalDate date = body.timestamp().toLocalDate();
        return employeeLogService.processPunches(
                date, body.timestamp().toLocalTime(), body.target(), body.employees(), body.punches());
    }

    /**
     * Half-hourly late-arrival check for the X-STORE-CODE store: claims and returns the alerts now due
     * (each employee's shift is alerted at most once). Called by the proxy backend's scheduler.
     */
    @PostMapping("/late-alerts")
    public LateAlertResponse lateAlerts() {
        return lateAlertService.claimDueAlerts(LocalDateTime.now(ZoneId.of(lateAlertsZone)));
    }
}
