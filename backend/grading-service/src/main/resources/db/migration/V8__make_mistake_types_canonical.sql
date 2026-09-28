-- A Tutor selects one canonical mistake type. The four legacy diagnostic
-- categories remain a compatibility grouping derived from that type.
ALTER TABLE approved_diagnostic_evidence
    ADD COLUMN mistake_type VARCHAR(40);

-- Preserve existing approved evidence by selecting the least-specific
-- canonical type for each previous category. Future writes always use the
-- explicit Tutor-selected canonical type.
UPDATE approved_diagnostic_evidence
SET mistake_type = CASE category
    WHEN 'CONCEPT' THEN 'CONCEPT_MISUNDERSTANDING'
    WHEN 'KEYWORD' THEN 'MISSING_KEY_POINT'
    WHEN 'EXPRESSION' THEN 'WEAK_EXPLANATION'
    WHEN 'APPLICATION' THEN 'INCOMPLETE_WORKING'
END;

ALTER TABLE approved_diagnostic_evidence
    ALTER COLUMN mistake_type SET NOT NULL;

ALTER TABLE approved_diagnostic_evidence
    ADD CONSTRAINT ck_approved_diagnostic_evidence_mistake_type CHECK (
        mistake_type IN (
            'CONCEPT_MISUNDERSTANDING',
            'CALCULATION_ERROR',
            'MISREAD_QUESTION',
            'INCOMPLETE_WORKING',
            'INCORRECT_FORMULA',
            'CARELESS_MISTAKE',
            'WEAK_EXPLANATION',
            'MISSING_KEY_POINT',
            'WRONG_UNITS',
            'ANSWER_FORMAT_ISSUE'
        )
    );

CREATE INDEX idx_approved_diagnostic_evidence_submission_type
    ON approved_diagnostic_evidence (submission_id, mistake_type);
