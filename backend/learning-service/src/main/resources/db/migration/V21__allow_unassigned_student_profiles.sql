ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS ck_student_profiles_owner;
ALTER TABLE student_profiles ALTER COLUMN tutor_id DROP NOT NULL;
ALTER TABLE student_profiles ADD CONSTRAINT ck_student_profiles_owner CHECK (tutor_id IS NULL OR tutor_id > 0);
