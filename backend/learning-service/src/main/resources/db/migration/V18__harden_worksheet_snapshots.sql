-- A worksheet is an assessment artifact.  Preserve the content and marks that
-- were selected at generation time rather than letting later question-bank
-- edits silently change an approved worksheet or its marking allocation.
ALTER TABLE worksheets
    ADD COLUMN worksheet_type VARCHAR(16) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE worksheets
    ADD COLUMN subject VARCHAR(80);

ALTER TABLE worksheets
    ADD CONSTRAINT ck_worksheets_type CHECK (
        worksheet_type IN ('STANDARD', 'DIAGNOSTIC')
    );

-- Generated worksheets have an authoritative class subject.  Historical
-- manually-created worksheets intentionally remain nullable: their source
-- class cannot be determined safely from an assignment alone.
UPDATE worksheets
SET subject = (
    SELECT tutor_classes.subject
    FROM worksheet_generation_requests
    JOIN tutor_classes
        ON tutor_classes.id = worksheet_generation_requests.class_id
        AND tutor_classes.tutor_id = worksheet_generation_requests.tutor_id
    WHERE worksheet_generation_requests.id = worksheets.generation_request_id
)
WHERE generation_request_id IS NOT NULL;

ALTER TABLE worksheet_questions
    ADD COLUMN question_code_snapshot VARCHAR(120);

ALTER TABLE worksheet_questions
    ADD COLUMN prompt_snapshot VARCHAR(4000);

ALTER TABLE worksheet_questions
    ADD COLUMN question_type_snapshot VARCHAR(32);

ALTER TABLE worksheet_questions
    ADD COLUMN total_marks_snapshot DECIMAL(6, 2);

ALTER TABLE worksheet_questions
    ADD CONSTRAINT ck_worksheet_questions_snapshot_marks
        CHECK (total_marks_snapshot IS NULL OR total_marks_snapshot > 0);

-- Backfill all existing rows.  Keeping the columns nullable allows legacy
-- imports with an unavailable source question to continue to be read safely.
UPDATE worksheet_questions
SET question_code_snapshot = (
        SELECT code FROM questions WHERE questions.id = worksheet_questions.question_id
    ),
    prompt_snapshot = (
        SELECT prompt FROM questions WHERE questions.id = worksheet_questions.question_id
    ),
    question_type_snapshot = (
        SELECT question_type FROM questions WHERE questions.id = worksheet_questions.question_id
    ),
    total_marks_snapshot = (
        SELECT total_marks FROM questions WHERE questions.id = worksheet_questions.question_id
    );

-- A question can appear only once and in only one position in an individual
-- worksheet.  The question constraint already existed; this closes the
-- independent ordering-integrity gap.
ALTER TABLE worksheet_questions
    ADD CONSTRAINT uk_worksheet_questions_position UNIQUE (worksheet_id, position);
