-- OCR calls may take seconds and must never run inside an upload transaction.
-- These fields make the retained source pages a durable, retryable work item.
ALTER TABLE question_import_batches
    DROP CONSTRAINT ck_question_import_batch_status;

ALTER TABLE question_import_batches
    ADD CONSTRAINT ck_question_import_batch_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'READY_FOR_REVIEW', 'FAILED', 'IMPORTED'));

ALTER TABLE question_import_batches
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE question_import_batches
    ADD COLUMN processing_started_at TIMESTAMP(6);

ALTER TABLE question_import_batches
    ADD COLUMN next_attempt_at TIMESTAMP(6);

ALTER TABLE question_import_batches
    ADD COLUMN last_processing_error VARCHAR(1000);

ALTER TABLE question_import_batches
    ADD CONSTRAINT ck_question_import_batch_attempt_count CHECK (attempt_count >= 0);

CREATE INDEX idx_question_import_batch_work
    ON question_import_batches (status, next_attempt_at, created_at);
