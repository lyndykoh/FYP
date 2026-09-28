-- Import sources and review drafts may contain unpublished assessment content.
-- Legacy rows have no trustworthy owner and are intentionally not exposed by
-- the owner-scoped API.
ALTER TABLE question_import_batches
    ADD COLUMN created_by_tutor_id BIGINT;

CREATE INDEX idx_question_import_batches_owner
    ON question_import_batches (created_by_tutor_id, id);
