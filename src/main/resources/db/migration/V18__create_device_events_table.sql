-- Idempotency ledger for Hikvision device push notifications (POST /api/device-events).
-- One row per accepted event; source_key is the dedup key (device id + serial, or a time/person hash).
CREATE TABLE device_events (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    source_key       VARCHAR(200) NOT NULL,
    serial_no        BIGINT,
    employee_code    VARCHAR(255),
    event_time       TIMESTAMP,
    major_event_type INTEGER,
    sub_event_type   INTEGER,
    received_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_device_events PRIMARY KEY (id),
    CONSTRAINT uq_device_events_source_key UNIQUE (source_key)
);

CREATE INDEX idx_device_events_event_time ON device_events(event_time);
