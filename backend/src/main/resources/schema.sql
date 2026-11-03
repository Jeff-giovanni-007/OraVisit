CREATE TABLE IF NOT EXISTS employees (
    id            BIGSERIAL    PRIMARY KEY,
    matricule     VARCHAR(20)  NOT NULL,
    first_name    VARCHAR(60)  NOT NULL,
    last_name     VARCHAR(60)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    phone         VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(100),
    role          VARCHAR(20)  NOT NULL,
    direction     VARCHAR(64)  NOT NULL,
    poste         VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_employees_matricule UNIQUE (matricule),
    CONSTRAINT uk_employees_email UNIQUE (email),
    CONSTRAINT ck_employees_role CHECK (role IN ('ADMIN', 'RECEPTION', 'DEFAULT'))
);

CREATE TABLE IF NOT EXISTS visitors (
    id                     BIGSERIAL    PRIMARY KEY,
    first_name             VARCHAR(60)  NOT NULL,
    last_name              VARCHAR(60)  NOT NULL,
    email                  VARCHAR(120),
    phone                  VARCHAR(30)  NOT NULL,
    identity_document_type VARCHAR(20)  NOT NULL,
    identity_card_number   VARCHAR(40)  NOT NULL,
    created_by_id          BIGINT,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_visitors_identity_document UNIQUE (identity_document_type, identity_card_number),
    CONSTRAINT fk_visitors_created_by FOREIGN KEY (created_by_id) REFERENCES employees (id),
    CONSTRAINT ck_visitors_document_type CHECK (
        identity_document_type IN ('CNI', 'PASSEPORT', 'PERMIS', 'CARTE_CONSULAIRE', 'AUTRE')
    )
);

CREATE TABLE IF NOT EXISTS visits (
    id                  BIGSERIAL    PRIMARY KEY,
    visitor_id          BIGINT       NOT NULL,
    host_employee_id    BIGINT       NOT NULL,
    registered_by_id    BIGINT       NOT NULL,
    direction           VARCHAR(64)  NOT NULL,
    purpose             VARCHAR(255) NOT NULL,
    badge_number        VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    check_in_at         TIMESTAMPTZ  NOT NULL,
    check_out_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_visits_visitor FOREIGN KEY (visitor_id) REFERENCES visitors (id),
    CONSTRAINT fk_visits_host_employee FOREIGN KEY (host_employee_id) REFERENCES employees (id),
    CONSTRAINT fk_visits_registered_by FOREIGN KEY (registered_by_id) REFERENCES employees (id),
    CONSTRAINT ck_visits_status CHECK (status IN ('EN_COURS', 'TERMINEE', 'ANNULEE')),
    CONSTRAINT ck_visits_check_out_after_check_in CHECK (check_out_at IS NULL OR check_out_at >= check_in_at),
    CONSTRAINT ck_visits_cancellation CHECK (
        CASE WHEN status = 'ANNULEE'
             THEN cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL AND check_out_at IS NULL
                  AND cancelled_at >= check_in_at
             ELSE cancelled_at IS NULL AND cancellation_reason IS NULL
        END
    )
);

CREATE TABLE IF NOT EXISTS visitor_identity_documents (
    visitor_id   BIGINT      PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL,
    content      BYTEA       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_visitor_identity_documents_visitor FOREIGN KEY (visitor_id) REFERENCES visitors (id),
    CONSTRAINT ck_visitor_identity_documents_type CHECK (
        content_type IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf')
    )
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL    PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    action          VARCHAR(40)  NOT NULL,
    outcome         VARCHAR(10)  NOT NULL,
    actor_id        BIGINT,
    actor_matricule VARCHAR(40)  NOT NULL,
    actor_name      VARCHAR(140),
    actor_role      VARCHAR(20),
    target_type     VARCHAR(30)  NOT NULL,
    target_id       BIGINT,
    target_label    VARCHAR(160),
    details         VARCHAR(500),
    ip_address      VARCHAR(45),
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX IF NOT EXISTS ix_employees_role ON employees (role);
CREATE INDEX IF NOT EXISTS ix_employees_direction ON employees (direction);

CREATE INDEX IF NOT EXISTS ix_visitors_last_name_lower ON visitors (LOWER(last_name));
CREATE INDEX IF NOT EXISTS ix_visitors_first_name_lower ON visitors (LOWER(first_name));
CREATE INDEX IF NOT EXISTS ix_visitors_phone ON visitors (phone);
CREATE INDEX IF NOT EXISTS ix_visitors_identity_number_lower ON visitors (LOWER(identity_card_number));
CREATE INDEX IF NOT EXISTS ix_visitors_created_by_id ON visitors (created_by_id);

CREATE INDEX IF NOT EXISTS ix_visits_status ON visits (status);
CREATE INDEX IF NOT EXISTS ix_visits_check_in_at ON visits (check_in_at);
CREATE INDEX IF NOT EXISTS ix_visits_visitor_id ON visits (visitor_id);
CREATE INDEX IF NOT EXISTS ix_visits_host_employee_id ON visits (host_employee_id);
CREATE INDEX IF NOT EXISTS ix_visits_direction ON visits (direction);
CREATE INDEX IF NOT EXISTS ix_visits_registered_by_id ON visits (registered_by_id);

CREATE INDEX IF NOT EXISTS ix_audit_logs_occurred_at ON audit_logs (occurred_at);
CREATE INDEX IF NOT EXISTS ix_audit_logs_action ON audit_logs (action);
CREATE INDEX IF NOT EXISTS ix_audit_logs_actor_id ON audit_logs (actor_id);
CREATE INDEX IF NOT EXISTS ix_audit_logs_target ON audit_logs (target_type, target_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_visits_badge_active ON visits (badge_number) WHERE status = 'EN_COURS';
CREATE UNIQUE INDEX IF NOT EXISTS ux_visits_visitor_active ON visits (visitor_id) WHERE status = 'EN_COURS';

INSERT INTO employees (matricule, first_name, last_name, email, phone, password_hash, role, direction, poste,
                       created_at, updated_at)
SELECT 'ADM001', 'Administrateur', 'OraVisit', 'admin@orabank.tg', '+228 00 00 00 00',
       '$2a$10$wnFyIq81JESREjhN1R2KxuppUhSg.eXwGXOX9ThA.ObxxzqgTJyXS',
       'ADMIN', 'DIRECTION_SYSTEMES_INFORMATION', 'Administrateur de l''application', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM employees);