CREATE TABLE check_timings (
    id      UUID    PRIMARY KEY,
    time    TIME    NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);
