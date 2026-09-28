ALTER TABLE worksheet_generation_requests
    ADD COLUMN difficulty VARCHAR(16);

ALTER TABLE worksheet_generation_requests
    ADD CONSTRAINT ck_worksheet_generation_difficulty
        CHECK (difficulty IS NULL OR difficulty IN ('FOUNDATION', 'APPLICATION', 'CHALLENGE'));
