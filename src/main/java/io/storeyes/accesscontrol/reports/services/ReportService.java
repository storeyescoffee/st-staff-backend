package io.storeyes.accesscontrol.reports.services;

import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.employees.repositories.EmployeeRepository;
import io.storeyes.accesscontrol.logs.entities.EmployeeLog;
import io.storeyes.accesscontrol.logs.entities.LogStatus;
import io.storeyes.accesscontrol.logs.repositories.EmployeeLogRepository;
import io.storeyes.accesscontrol.reports.dto.ReportSummaryRow;
import io.storeyes.accesscontrol.workmodes.entities.WorkMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeLogRepository employeeLogRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<ReportSummaryRow> getSummary(LocalDate from, LocalDate to, UUID employeeId) {
        List<EmployeeLog> logs = employeeId != null
                ? employeeLogRepository.findByDateBetweenAndEmployee_Id(from, to, employeeId)
                : employeeLogRepository.findByDateBetween(from, to);

        // Group logs by employee
        Map<UUID, List<EmployeeLog>> byEmployee = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        // Ensure all employees appear in the result even if they have no logs
        List<Employee> allEmployees = employeeId != null
                ? employeeRepository.findById(employeeId).map(List::of).orElse(List.of())
                : employeeRepository.findAll();

        List<ReportSummaryRow> result = new ArrayList<>();
        for (Employee emp : allEmployees) {
            List<EmployeeLog> empLogs = byEmployee.getOrDefault(emp.getId(), List.of());
            result.add(aggregate(emp, empLogs));
        }
        return result;
    }

    private ReportSummaryRow aggregate(Employee emp, List<EmployeeLog> logs) {
        int daysWorked = 0;
        int totalMinutes = 0;
        int lateTotalMinutes = 0;
        int lateDays = 0;
        int missingOuts = 0;
        int absences = 0;
        int overtimeMinutes = 0;

        for (EmployeeLog log : logs) {
            if (log.getLoggedIn() != null) {
                daysWorked++;
            }
            if (log.getDuration() != null) {
                totalMinutes += log.getDuration();

                WorkMode wm = log.getWorkMode();
                if (wm != null && wm.getStartTime() != null && wm.getEndTime() != null) {
                    int planned = (int) ChronoUnit.MINUTES.between(wm.getStartTime(), wm.getEndTime());
                    overtimeMinutes += Math.max(0, log.getDuration() - planned);
                }
            }
            if (log.getStatus() == LogStatus.LATE) {
                lateDays++;
                WorkMode wm = log.getWorkMode();
                if (wm != null && wm.getStartTime() != null && log.getLoggedIn() != null) {
                    int minutesLate = (int) ChronoUnit.MINUTES.between(wm.getStartTime(), log.getLoggedIn());
                    lateTotalMinutes += Math.max(0, minutesLate);
                }
            }
            if (log.getStatus() == LogStatus.MISSED_OUT) {
                missingOuts++;
            }
            if (log.getStatus() == LogStatus.ABSENT) {
                absences++;
            }
        }

        return new ReportSummaryRow(
                emp.getId(),
                emp.getName(),
                emp.getCode(),
                daysWorked,
                totalMinutes,
                lateTotalMinutes,
                lateDays,
                missingOuts,
                absences,
                overtimeMinutes
        );
    }
}
