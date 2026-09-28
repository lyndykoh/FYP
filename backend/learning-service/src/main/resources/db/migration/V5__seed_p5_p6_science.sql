-- Reviewed against Singapore MOE, Science Teaching & Learning Syllabus Primary 2023,
-- Table 2: An Overview of the topics in the Primary Science Syllabus.
-- https://www.moe.gov.sg/-/media/files/primary/syllabus/primary-science-syllabus-2023_may24.pdf

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
VALUES
    ('SCI', 'Science', 'SUBJECT', 0, NULL, NULL, 10,
     'MOE_PRIMARY_SCIENCE_2023',
     'https://www.moe.gov.sg/-/media/files/primary/syllabus/primary-science-syllabus-2023_may24.pdf');

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5', 'Primary 5', 'LEVEL', 1, id, depth, 10,
       curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6', 'Primary 6', 'LEVEL', 1, id, depth, 20,
       curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_CYCLES', 'Cycles', 'THEME', 2, id, depth, 10,
       curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS', 'Systems', 'THEME', 2, id, depth, 20,
       curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_ENERGY', 'Energy', 'THEME', 2, id, depth, 10,
       curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_INTERACTIONS', 'Interactions', 'THEME', 2, id, depth, 20,
       curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_CYCLES_PLANTS_ANIMALS', 'Cycles in plants and animals', 'TOPIC',
       3, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_CYCLES';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_CYCLES_MATTER_WATER', 'Cycles in matter and water', 'TOPIC',
       3, id, depth, 20, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_CYCLES';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS_PLANT', 'Plant system', 'TOPIC',
       3, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_SYSTEMS';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS_HUMAN', 'Human system', 'TOPIC',
       3, id, depth, 20, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_SYSTEMS';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS_ELECTRICAL', 'Electrical system', 'TOPIC',
       3, id, depth, 30, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_SYSTEMS';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_ENERGY_FORMS_USES', 'Energy forms and uses', 'TOPIC',
       3, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_ENERGY';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_ENERGY_CONVERSION', 'Energy conversion', 'TOPIC',
       3, id, depth, 20, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_ENERGY';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_INTERACTIONS_FORCES', 'Interaction of forces', 'TOPIC',
       3, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_INTERACTIONS';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_INTERACTIONS_ENVIRONMENT', 'Interactions within the environment', 'TOPIC',
       3, id, depth, 20, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_INTERACTIONS';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_CYCLES_PLANTS_ANIMALS_REPRODUCTION', 'Reproduction', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_CYCLES_PLANTS_ANIMALS';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_CYCLES_MATTER_WATER_WATER', 'Water', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_CYCLES_MATTER_WATER';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS_PLANT_RESPIRATORY_CIRCULATORY',
       'Respiratory and circulatory systems', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_SYSTEMS_PLANT';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P5_SYSTEMS_HUMAN_RESPIRATORY_CIRCULATORY',
       'Respiratory and circulatory systems', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P5_SYSTEMS_HUMAN';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_ENERGY_FORMS_USES_PHOTOSYNTHESIS', 'Photosynthesis', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_ENERGY_FORMS_USES';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_INTERACTIONS_FORCES_FRICTIONAL', 'Frictional force', 'SUBTOPIC',
       4, id, depth, 10, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_INTERACTIONS_FORCES';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_INTERACTIONS_FORCES_GRAVITATIONAL', 'Gravitational force', 'SUBTOPIC',
       4, id, depth, 20, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_INTERACTIONS_FORCES';

INSERT INTO syllabus_topics
    (code, name, node_type, depth, parent_id, parent_depth, sort_order,
     curriculum_version, source_reference)
SELECT 'SCI_P6_INTERACTIONS_FORCES_ELASTIC_SPRING', 'Elastic spring force', 'SUBTOPIC',
       4, id, depth, 30, curriculum_version, source_reference
FROM syllabus_topics WHERE code = 'SCI_P6_INTERACTIONS_FORCES';
