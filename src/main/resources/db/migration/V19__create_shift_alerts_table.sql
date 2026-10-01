-- Ledger of the late-arrival alerts sent by the half-hourly shift check (POST /api/employee-logs/late-alerts).
-- One row per employee, day and shift: the unique key is what guarantees each alert goes out once.
CREATE TABLE shift_alerts (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    date         DATE        NOT NULL,
    employee_id  UUID        NOT NULL,
    work_mode_id UUID        NOT NULL,
    status       VARCHAR(20) NOT NULL, -- LogStatus at alert time: LATE (arrived late) | ABSENT (not arrived)
    sent_at      TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT pk_shift_alerts PRIMARY KEY (id),
    CONSTRAINT uq_shift_alerts_date_emp_wm UNIQUE (date, employee_id, work_mode_id)
);
