-- Grading remains the source of submissions.  Learning stores only the
-- identifiers needed to present a Student's approved worksheet outcome.
-- These columns remain nullable for pre-existing historical projections,
-- which are deliberately excluded from worksheet score calculation.
ALTER TABLE mastery_approved_results
    ADD COLUMN worksheet_id BIGINT;

ALTER TABLE mastery_approved_results
    ADD COLUMN worksheet_question_id BIGINT;

ALTER TABLE marking_review_status_projection
    ADD COLUMN worksheet_id BIGINT;

ALTER TABLE mastery_approved_results
    ADD CONSTRAINT ck_mastery_approved_results_worksheet_id
        CHECK (worksheet_id IS NULL OR worksheet_id > 0);

ALTER TABLE mastery_approved_results
    ADD CONSTRAINT ck_mastery_approved_results_worksheet_question_id
        CHECK (worksheet_question_id IS NULL OR worksheet_question_id > 0);

ALTER TABLE marking_review_status_projection
    ADD CONSTRAINT ck_marking_review_projection_worksheet_id
        CHECK (worksheet_id IS NULL OR worksheet_id > 0);

CREATE INDEX idx_mastery_approved_results_student_worksheet_active
    ON mastery_approved_results (student_profile_id, worksheet_id, active, worksheet_question_id, reviewed_at);

CREATE INDEX idx_marking_review_projection_student_worksheet
    ON marking_review_status_projection (student_profile_id, worksheet_id, requested_at);
