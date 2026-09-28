-- Flyway creates the configured default schema before running this migration.
-- This marker makes a clean learning-service baseline explicit and auditable.
CREATE TABLE learning_schema_metadata (
    id SMALLINT PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_learning_schema_metadata_singleton CHECK (id = 1)
);

INSERT INTO learning_schema_metadata (id, service_name)
VALUES (1, 'learning-service');
