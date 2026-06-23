package io.storeyes.accesscontrol.anomalies.services;

import io.storeyes.accesscontrol.anomalies.dto.AnomalyPatchRequest;
import io.storeyes.accesscontrol.anomalies.dto.AnomalyResponse;
import io.storeyes.accesscontrol.anomalies.entities.Anomaly;
import io.storeyes.accesscontrol.anomalies.repositories.AnomalyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnomalyService {

    private final AnomalyRepository anomalyRepository;

    /**
     * Returns anomalies in the date range, optionally filtered by employee and handled state.
     *
     * @param from        start date (inclusive)
     * @param to          end date (inclusive)
     * @param employeeId  if non-null, restricts to this employee's logs
     * @param handled     if non-null, restricts to handled (true) or open (false) anomalies
     */
    @Transactional(readOnly = true)
    public List<AnomalyResponse> getAnomalies(LocalDate from, LocalDate to, UUID employeeId, Boolean handled) {
        return anomalyRepository.findByEmployeeLog_DateBetween(from, to)
                .stream()
                .filter(a -> employeeId == null || a.getEmployeeLog().getEmployee().getId().equals(employeeId))
                .filter(a -> handled == null || a.isHandled() == handled)
                .map(AnomalyResponse::from)
                .toList();
    }

    /**
     * Partially updates an anomaly: can mark it handled/unhandled, set reason and/or description.
     * Sets handledAt timestamp when transitioning to handled.
     */
    @Transactional
    public AnomalyResponse patch(UUID id, AnomalyPatchRequest req) {
        Anomaly anomaly = anomalyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anomaly not found: " + id));

        if (req.isHandled() != null) {
            anomaly.setHandled(req.isHandled());
            if (req.isHandled() && anomaly.getHandledAt() == null) {
                anomaly.setHandledAt(LocalDateTime.now());
            } else if (!req.isHandled()) {
                anomaly.setHandledAt(null);
                anomaly.setReason(null);
                anomaly.setDescription(null);
            }
        }
        if (req.reason() != null) {
            anomaly.setReason(req.reason());
        }
        if (req.description() != null) {
            anomaly.setDescription(req.description().isBlank() ? null : req.description().trim());
        }

        return AnomalyResponse.from(anomalyRepository.save(anomaly));
    }
}
