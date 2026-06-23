CREATE TABLE anomalies (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_log_id UUID      NOT NULL REFERENCES employee_logs(id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL,   -- AnomalyType: LATE | ABSENCE | MISSING_OUT
    reason        VARCHAR(30),            -- AnomalyReason: nullable until handled
    description   TEXT,
    is_handled    BOOLEAN     NOT NULL DEFAULT FALSE,
    handled_at    TIMESTAMP
);

CREATE INDEX idx_anomalies_log     ON anomalies(employee_log_id);
CREATE INDEX idx_anomalies_handled ON anomalies(is_handled);
