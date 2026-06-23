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
