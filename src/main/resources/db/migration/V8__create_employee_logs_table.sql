CREATE TABLE employee_logs (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    date        DATE        NOT NULL,
    employee_id UUID        NOT NULL REFERENCES employees(id),
    work_mode_id UUID       REFERENCES work_modes(id),
    logged_in   TIME,
    logged_out  TIME,
    status      VARCHAR(20),
    CONSTRAINT pk_employee_logs PRIMARY KEY (id),
    CONSTRAINT uq_employee_logs_date_emp UNIQUE (date, employee_id)
);
