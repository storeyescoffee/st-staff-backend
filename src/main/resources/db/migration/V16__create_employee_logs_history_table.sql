CREATE TABLE employee_logs_history (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    created_at       TIMESTAMP   NOT NULL DEFAULT now(),
    date             DATE        NOT NULL,
    shift_start_time TIME,
    shift_end_time   TIME,
    method           VARCHAR(10), -- PunchMethod: IN | OUT, null for untargeted (legacy) batches
    logs             JSONB       NOT NULL,
    notification     JSONB,
    CONSTRAINT pk_employee_logs_history PRIMARY KEY (id)
);

CREATE INDEX idx_employee_logs_history_date ON employee_logs_history(date);
