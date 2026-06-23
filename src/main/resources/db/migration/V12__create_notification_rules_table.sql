-- Single-row table holding the on/off state of each notification rule.
-- "group" is a reserved word in SQL, so that rule's column is named group_alert.
CREATE TABLE notification_rules (
    id          UUID    PRIMARY KEY,
    late        BOOLEAN NOT NULL DEFAULT TRUE,
    absence     BOOLEAN NOT NULL DEFAULT TRUE,
    group_alert BOOLEAN NOT NULL DEFAULT TRUE,
    dnd         BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO notification_rules (id, late, absence, group_alert, dnd)
VALUES ('00000000-0000-0000-0000-000000000001', TRUE, TRUE, TRUE, TRUE);
