-- Correct the inherited Plant-system duplicate without rewriting the applied V5 seed.
-- The canonical Human-system leaf remains SCI_P5_SYSTEMS_HUMAN_RESPIRATORY_CIRCULATORY.
-- Retaining the inactive legacy row preserves any historical foreign-key references.
UPDATE syllabus_topics
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'SCI_P5_SYSTEMS_PLANT_RESPIRATORY_CIRCULATORY'
  AND active = TRUE;

-- Add one stable MVP leaf to each previously leafless topic.  These rows deliberately
-- inherit the reviewed curriculum version and source reference from their topic.
INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS_PLANT_TRANSPORT', 'Plant transport', 'SUBTOPIC',
       4, id, depth, 20, curriculum_version, source_reference
FROM syllabus_topics
WHERE code = 'SCI_P5_SYSTEMS_PLANT';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS_ELECTRICAL_CIRCUITS', 'Electrical circuits', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics
WHERE code = 'SCI_P5_SYSTEMS_ELECTRICAL';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_ENERGY_CONVERSION_TRANSFORMATIONS', 'Energy transformations', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics
WHERE code = 'SCI_P6_ENERGY_CONVERSION';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_INTERACTIONS_ENVIRONMENT_INTERDEPENDENCE', 'Interdependence in the environment', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics
WHERE code = 'SCI_P6_INTERACTIONS_ENVIRONMENT';
