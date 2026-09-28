-- A tutor-approved mistake type is the canonical diagnostic signal.  The
-- original four-value category remains a derived compatibility grouping for
-- existing insight clients and older grading deliveries.
ALTER TABLE mastery_diagnostic_evidence
    ADD COLUMN mistake_type VARCHAR(40);

UPDATE mastery_diagnostic_evidence
SET mistake_type = CASE diagnostic_category
    WHEN 'CONCEPT' THEN 'CONCEPT_MISUNDERSTANDING'
    WHEN 'KEYWORD' THEN 'MISSING_KEY_POINT'
    WHEN 'EXPRESSION' THEN 'WEAK_EXPLANATION'
    WHEN 'APPLICATION' THEN 'INCOMPLETE_WORKING'
END;

ALTER TABLE mastery_diagnostic_evidence
    ALTER COLUMN mistake_type SET NOT NULL;

ALTER TABLE mastery_diagnostic_evidence
    DROP CONSTRAINT uk_mastery_diagnostic_evidence_submission_category;

ALTER TABLE mastery_diagnostic_evidence
    ADD CONSTRAINT uk_mastery_diagnostic_evidence_submission_type
        UNIQUE (source_submission_id, mistake_type);

ALTER TABLE mastery_diagnostic_evidence
    ADD CONSTRAINT ck_mastery_diagnostic_evidence_type CHECK (
        mistake_type IN (
            'CONCEPT_MISUNDERSTANDING', 'CALCULATION_ERROR', 'MISREAD_QUESTION',
            'INCOMPLETE_WORKING', 'INCORRECT_FORMULA', 'CARELESS_MISTAKE',
            'WEAK_EXPLANATION', 'MISSING_KEY_POINT', 'WRONG_UNITS',
            'ANSWER_FORMAT_ISSUE'
        )
    );

-- Keep the broad grouping honest even for non-JPA writers.
ALTER TABLE mastery_diagnostic_evidence
    ADD CONSTRAINT ck_mastery_diagnostic_evidence_type_category CHECK (
        (mistake_type = 'CONCEPT_MISUNDERSTANDING' AND diagnostic_category = 'CONCEPT')
        OR (mistake_type = 'MISSING_KEY_POINT' AND diagnostic_category = 'KEYWORD')
        OR (mistake_type IN ('WEAK_EXPLANATION', 'WRONG_UNITS', 'ANSWER_FORMAT_ISSUE') AND diagnostic_category = 'EXPRESSION')
        OR (mistake_type IN ('CALCULATION_ERROR', 'MISREAD_QUESTION', 'INCOMPLETE_WORKING', 'INCORRECT_FORMULA', 'CARELESS_MISTAKE')
            AND diagnostic_category = 'APPLICATION')
    );

DROP INDEX idx_mastery_diagnostic_evidence_student_topic;
CREATE INDEX idx_mastery_diagnostic_evidence_student_topic_type
    ON mastery_diagnostic_evidence (student_profile_id, mastery_record_id, mistake_type, created_at DESC);
