-- Duplicate detection is advisory only. These local fingerprints preserve the
-- evidence that produced a warning without changing a draft's import status.
ALTER TABLE question_import_source_pages
    ADD COLUMN source_checksum VARCHAR(64);
ALTER TABLE question_import_source_pages
    ADD COLUMN page_image_sha256 VARCHAR(64);
ALTER TABLE question_import_source_pages
    ADD COLUMN page_image_perceptual_hash VARCHAR(16);

ALTER TABLE question_import_diagram_crops
    ADD COLUMN crop_sha256 VARCHAR(64);
ALTER TABLE question_import_diagram_crops
    ADD COLUMN crop_perceptual_hash VARCHAR(16);

ALTER TABLE question_images
    ADD COLUMN image_sha256 VARCHAR(64);
ALTER TABLE question_images
    ADD COLUMN image_perceptual_hash VARCHAR(16);

CREATE INDEX idx_question_import_page_source_identity
    ON question_import_source_pages (source_checksum, source_page_number);
CREATE INDEX idx_question_import_page_image_sha256
    ON question_import_source_pages (page_image_sha256);
CREATE INDEX idx_question_import_crop_sha256
    ON question_import_diagram_crops (crop_sha256);
CREATE INDEX idx_question_images_sha256
    ON question_images (image_sha256);
