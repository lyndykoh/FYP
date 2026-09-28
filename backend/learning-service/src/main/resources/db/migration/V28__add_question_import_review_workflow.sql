-- OCR output is retained as an immutable suggestion. Tutor edits live in the
-- existing reviewed columns so the source proposal remains auditable.
ALTER TABLE question_import_candidates
    ADD COLUMN suggested_prompt VARCHAR(4000);
ALTER TABLE question_import_candidates
    ADD COLUMN suggested_model_answer VARCHAR(4000);
ALTER TABLE question_import_candidates
    ADD COLUMN suggested_total_marks NUMERIC(6,2);
ALTER TABLE question_import_candidates
    ADD COLUMN reviewed_question_type VARCHAR(32);
ALTER TABLE question_import_candidates
    ADD COLUMN reviewed_difficulty VARCHAR(16);
ALTER TABLE question_import_candidates
    ADD COLUMN prompt_confidence INTEGER;
ALTER TABLE question_import_candidates
    ADD COLUMN model_answer_confidence INTEGER;
ALTER TABLE question_import_candidates
    ADD COLUMN classification_confidence INTEGER;
ALTER TABLE question_import_candidates
    ADD COLUMN marks_confidence INTEGER;
ALTER TABLE question_import_candidates
    ADD COLUMN parent_candidate_id BIGINT;
ALTER TABLE question_import_candidates
    ADD COLUMN superseded_by_candidate_id BIGINT;
ALTER TABLE question_import_candidates
    ADD COLUMN rejected_from_status VARCHAR(24);
ALTER TABLE question_import_candidates
    ADD COLUMN rejected_reason VARCHAR(500);
ALTER TABLE question_import_candidates
    ADD COLUMN rejected_at TIMESTAMP(6);

UPDATE question_import_candidates
SET suggested_prompt = prompt,
    suggested_model_answer = model_answer,
    suggested_total_marks = total_marks,
    reviewed_question_type = suggested_question_type,
    reviewed_difficulty = suggested_difficulty;

ALTER TABLE question_import_candidates
    ALTER COLUMN suggested_prompt SET NOT NULL;
ALTER TABLE question_import_candidates
    ALTER COLUMN suggested_model_answer SET NOT NULL;
ALTER TABLE question_import_candidates
    ALTER COLUMN suggested_total_marks SET NOT NULL;
ALTER TABLE question_import_candidates
    ALTER COLUMN reviewed_question_type SET NOT NULL;
ALTER TABLE question_import_candidates
    ALTER COLUMN reviewed_difficulty SET NOT NULL;

ALTER TABLE question_import_candidates
    ADD CONSTRAINT ck_question_import_candidate_status_review
        CHECK (status IN ('READY_FOR_REVIEW', 'UNCERTAIN', 'FAILED', 'IMPORTED', 'REJECTED', 'SUPERSEDED'));
ALTER TABLE question_import_candidates
    ADD CONSTRAINT ck_question_import_candidate_field_confidence
        CHECK ((prompt_confidence IS NULL OR prompt_confidence BETWEEN 0 AND 100)
           AND (model_answer_confidence IS NULL OR model_answer_confidence BETWEEN 0 AND 100)
           AND (classification_confidence IS NULL OR classification_confidence BETWEEN 0 AND 100)
           AND (marks_confidence IS NULL OR marks_confidence BETWEEN 0 AND 100));
ALTER TABLE question_import_candidates
    ADD CONSTRAINT fk_question_import_candidate_parent
        FOREIGN KEY (parent_candidate_id) REFERENCES question_import_candidates (id);
ALTER TABLE question_import_candidates
    ADD CONSTRAINT fk_question_import_candidate_superseded_by
        FOREIGN KEY (superseded_by_candidate_id) REFERENCES question_import_candidates (id);

-- Replace the old status check rather than attempting to alter it in place.
ALTER TABLE question_import_candidates
    DROP CONSTRAINT ck_question_import_candidate_status;

ALTER TABLE question_import_diagram_crops
    ADD COLUMN original_candidate_id BIGINT;

UPDATE question_import_diagram_crops
SET original_candidate_id = candidate_id;

ALTER TABLE question_import_diagram_crops
    ALTER COLUMN original_candidate_id SET NOT NULL;
ALTER TABLE question_import_diagram_crops
    ADD CONSTRAINT fk_question_import_crop_original_candidate
        FOREIGN KEY (original_candidate_id) REFERENCES question_import_candidates (id);

CREATE INDEX idx_question_import_candidate_lineage
    ON question_import_candidates (batch_id, parent_candidate_id, superseded_by_candidate_id);
