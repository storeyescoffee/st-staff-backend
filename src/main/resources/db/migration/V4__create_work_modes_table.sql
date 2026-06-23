CREATE TABLE work_modes (
    id                UUID         NOT NULL,
    name              VARCHAR(255) NOT NULL,
    start_time        TIME,
    end_time          TIME,
    tolerant_late     INTEGER,
    followed_up       BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_employee INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_work_modes PRIMARY KEY (id)
);
