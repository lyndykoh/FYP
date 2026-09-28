CREATE TABLE marking_review_status_projection (
    source_submission_id BIGINT PRIMARY KEY,
    tutor_id BIGINT NOT NULL,
    student_profile_id BIGINT NOT NULL,
    revision INTEGER NOT NULL,
    review_state VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_marking_review_projection_source CHECK (source_submission_id > 0),
    CONSTRAINT ck_marking_review_projection_tutor CHECK (tutor_id > 0),
    CONSTRAINT ck_marking_review_projection_revision CHECK (revision > 0),
    CONSTRAINT ck_marking_review_projection_state CHECK (review_state IN ('PENDING_REVIEW', 'RESOLVED')),
    CONSTRAINT fk_marking_review_projection_student FOREIGN KEY (student_profile_id)
        REFERENCES student_profiles (id) ON DELETE CASCADE
);

CREATE INDEX idx_marking_review_projection_overdue
    ON marking_review_status_projection (tutor_id, review_state, requested_at);
