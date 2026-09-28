ALTER TABLE submission_documents
    DROP CONSTRAINT IF EXISTS ck_submission_documents_status;

ALTER TABLE submission_documents
    ALTER COLUMN status TYPE VARCHAR(24);

ALTER TABLE submission_documents
    ADD CONSTRAINT ck_submission_documents_status
    CHECK (status IN ('UPLOADING', 'READY', 'SUBMITTED_FOR_REVIEW'));
