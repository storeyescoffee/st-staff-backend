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
