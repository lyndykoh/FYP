-- A Tutor submission is always made in the context of one tutor-owned class.
-- The column remains nullable for historic Student and manual-result documents;
-- the aggregate enforces a class for new Tutor file uploads.
ALTER TABLE submission_documents ADD COLUMN class_id BIGINT;

CREATE INDEX idx_submission_documents_class_id
    ON submission_documents (class_id);
