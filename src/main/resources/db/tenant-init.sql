-- Merged schema init (V1–V11) for new tenant schemas.
-- Executed as a single batch; search_path is already set to the target schema.
-- Update this file alongside any new Vn__ migration that alters the schema shape.

CREATE TABLE roles (
    id   UUID         NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE work_modes (
    id                UUID         NOT NULL,
    name              VARCHAR(255) NOT NULL,
    start_time        TIME,
    end_time          TIME,
    tolerant_late     INTEGER,
    followed_up       BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_employee INTEGER      NOT NULL DEFAULT 0,
    color             VARCHAR(32),
    CONSTRAINT pk_work_modes PRIMARY KEY (id)
);

CREATE TABLE employees (
    id      UUID         NOT NULL,
    name    VARCHAR(255) NOT NULL,
    role_id UUID,
    code    VARCHAR(255) NOT NULL,
    synced  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_employees PRIMARY KEY (id),
    CONSTRAINT uq_employees_code UNIQUE (code),
    CONSTRAINT fk_employees_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE employee_credentials (
    employee_id UUID         NOT NULL,
    credential  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_employee_credentials PRIMARY KEY (employee_id, credential),
    CONSTRAINT fk_employee_credentials_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
);

CREATE TABLE work_mode_pairs (
    id             UUID NOT NULL,
    work_mode_a_id UUID NOT NULL,
    work_mode_b_id UUID NOT NULL,
    CONSTRAINT pk_work_mode_pairs PRIMARY KEY (id),
    CONSTRAINT fk_work_mode_pairs_a
        FOREIGN KEY (work_mode_a_id) REFERENCES work_modes (id) ON DELETE CASCADE,
    CONSTRAINT fk_work_mode_pairs_b
        FOREIGN KEY (work_mode_b_id) REFERENCES work_modes (id) ON DELETE CASCADE
);

CREATE TABLE schedules (
    id             UUID NOT NULL,
    number_of_week INTEGER,
    start_date     DATE,
    end_date       DATE,
    CONSTRAINT pk_schedules PRIMARY KEY (id)
);

CREATE TABLE schedule_details (
    id           UUID        NOT NULL,
    schedule_id  UUID        NOT NULL,
    employee_id  UUID        NOT NULL,
    dow          VARCHAR(16) NOT NULL,
    work_mode_id UUID,
    CONSTRAINT pk_schedule_details PRIMARY KEY (id),
    CONSTRAINT uq_schedule_details_cell UNIQUE (schedule_id, employee_id, dow),
    CONSTRAINT fk_schedule_details_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_details_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_details_work_mode
        FOREIGN KEY (work_mode_id) REFERENCES work_modes (id)
);

CREATE TABLE employee_logs (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    date         DATE        NOT NULL,
    employee_id  UUID        NOT NULL REFERENCES employees(id),
    work_mode_id UUID        REFERENCES work_modes(id),
    logged_in    TIME,
    logged_out   TIME,
    status       VARCHAR(20),
    duration     INTEGER,
    CONSTRAINT pk_employee_logs PRIMARY KEY (id),
    CONSTRAINT uq_employee_logs_date_emp UNIQUE (date, employee_id)
);

CREATE TABLE anomalies (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_log_id UUID        NOT NULL REFERENCES employee_logs(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL,
    reason          VARCHAR(30),
    description     TEXT,
    is_handled      BOOLEAN     NOT NULL DEFAULT FALSE,
    handled_at      TIMESTAMP
);

CREATE INDEX idx_anomalies_log     ON anomalies(employee_log_id);
CREATE INDEX idx_anomalies_handled ON anomalies(is_handled);

CREATE TABLE check_timings (
    id        UUID    PRIMARY KEY,
    time      TIME    NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);
