CREATE TABLE marking_component_keywords (
    marking_component_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    keyword VARCHAR(80) NOT NULL,
    CONSTRAINT pk_marking_component_keywords PRIMARY KEY (marking_component_id, position),
    CONSTRAINT uk_marking_component_keywords_value UNIQUE (marking_component_id, keyword),
    CONSTRAINT ck_marking_component_keywords_position CHECK (position >= 0),
    CONSTRAINT ck_marking_component_keywords_value CHECK (
        TRIM(keyword) <> '' AND keyword = LOWER(TRIM(keyword))
    ),
    CONSTRAINT fk_marking_component_keywords_component FOREIGN KEY (marking_component_id)
        REFERENCES marking_components (id) ON DELETE CASCADE
);

CREATE INDEX idx_marking_component_keywords_component
    ON marking_component_keywords (marking_component_id, position);
