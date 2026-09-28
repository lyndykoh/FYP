ALTER TABLE submission_documents
    ADD COLUMN manual_scope_key VARCHAR(160);

ALTER TABLE submission_documents
    DROP CONSTRAINT ck_submission_documents_source_type;

ALTER TABLE submission_documents
    ADD CONSTRAINT ck_submission_documents_source_type CHECK (
        source_type IN ('PDF', 'IMAGES', 'MANUAL')
    );

ALTER TABLE submission_documents
    ADD CONSTRAINT ck_submission_documents_manual_scope CHECK (
        (source_type = 'MANUAL' AND manual_scope_key IS NOT NULL AND TRIM(manual_scope_key) <> '')
        OR
        (source_type IN ('PDF', 'IMAGES') AND manual_scope_key IS NULL)
    );

ALTER TABLE submission_documents
    ADD CONSTRAINT uk_submission_documents_manual_scope UNIQUE (manual_scope_key);
