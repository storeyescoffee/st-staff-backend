CREATE TABLE employees (
    id      UUID         NOT NULL,
    name    VARCHAR(255) NOT NULL,
    role_id UUID         NOT NULL,
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
