-- Typed answer drafts are durable, but must never be visible in the Tutor
-- review queue until their owner explicitly submits them.
ALTER TABLE submissions DROP CONSTRAINT ck_submissions_review_status;
ALTER TABLE submissions DROP CONSTRAINT ck_submissions_approval_state;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_review_status CHECK (
        review_status IN ('DRAFT', 'PENDING_REVIEW', 'FLAGGED', 'APPROVED')
    );

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_approval_state CHECK (
        (
            review_status IN ('DRAFT', 'PENDING_REVIEW')
            AND approved_marks IS NULL
            AND approved_feedback IS NULL
            AND reviewed_by_user_id IS NULL
            AND reviewed_at IS NULL
        )
        OR
        (
            review_status = 'FLAGGED'
            AND approved_marks IS NULL
            AND approved_feedback IS NULL
            AND reviewed_by_user_id IS NOT NULL
            AND reviewed_at IS NOT NULL
        )
        OR
        (
            review_status = 'APPROVED'
            AND approved_marks IS NOT NULL
            AND approved_feedback IS NOT NULL
            AND reviewed_by_user_id IS NOT NULL
            AND reviewed_at IS NOT NULL
        )
    );
