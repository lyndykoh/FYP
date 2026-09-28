ALTER TABLE questions
    ADD COLUMN difficulty VARCHAR(16) NOT NULL DEFAULT 'FOUNDATION';

ALTER TABLE questions
    ADD CONSTRAINT ck_questions_difficulty
        CHECK (difficulty IN ('FOUNDATION', 'APPLICATION', 'CHALLENGE'));

CREATE INDEX idx_questions_difficulty_active
    ON questions (difficulty, archive_state, syllabus_topic_id);
