ALTER TABLE employees ADD COLUMN display_order INT NOT NULL DEFAULT 0;

-- Seed existing rows with a stable initial order (arbitrary but deterministic).
UPDATE employees e
SET display_order = ranked.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY name) AS rn
    FROM employees
) ranked
WHERE e.id = ranked.id;
