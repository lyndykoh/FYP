package com.fttranscendence.learning.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SyllabusIntegrationTest {

  private static final String VERSION = "MOE_PRIMARY_SCIENCE_2023";

  @Autowired private SyllabusTopicRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void seedsTheReviewedP5AndP6ScienceMvpTaxonomyWithStableNodeCounts() {
    SyllabusTopic science = topic("SCI");
    List<SyllabusTopic> levels = childrenOf(science);

    assertEquals(SyllabusTopic.NodeType.SUBJECT, science.getNodeType());
    assertEquals(0, science.getDepth());
    assertEquals(27, repository.countByCurriculumVersionAndActiveTrue(VERSION));
    assertEquals(List.of("SCI_P5", "SCI_P6"), codes(levels));
    assertEquals(List.of("Cycles", "Systems"), names(childrenOf(topic("SCI_P5"))));
    assertEquals(List.of("Energy", "Interactions"), names(childrenOf(topic("SCI_P6"))));
    assertEquals(
        Map.of(
            SyllabusTopic.NodeType.SUBJECT, 1L,
            SyllabusTopic.NodeType.LEVEL, 2L,
            SyllabusTopic.NodeType.THEME, 4L,
            SyllabusTopic.NodeType.TOPIC, 9L,
            SyllabusTopic.NodeType.SUBTOPIC, 11L),
        activeNodeCounts());

    assertEquals(
        List.of(
            "SCI_P5_CYCLES_MATTER_WATER",
            "SCI_P5_CYCLES_PLANTS_ANIMALS",
            "SCI_P5_SYSTEMS_ELECTRICAL",
            "SCI_P5_SYSTEMS_HUMAN",
            "SCI_P5_SYSTEMS_PLANT",
            "SCI_P6_ENERGY_CONVERSION",
            "SCI_P6_ENERGY_FORMS_USES",
            "SCI_P6_INTERACTIONS_ENVIRONMENT",
            "SCI_P6_INTERACTIONS_FORCES"),
        repository
            .findAllByNodeTypeAndActiveTrueOrderBySortOrderAscCodeAsc(SyllabusTopic.NodeType.TOPIC)
            .stream()
            .map(SyllabusTopic::getCode)
            .sorted()
            .toList());
    assertTrue(science.getSourceReference().contains("moe.gov.sg"));
    assertEquals(VERSION, science.getCurriculumVersion());
  }

  @Test
  void activeMvpTopicsAlwaysHaveAnActiveLeafAndTheHumanSystemKeepsTheCanonicalRespiratoryLeaf() {
    List<SyllabusTopic> topics =
        repository.findAllByNodeTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
            SyllabusTopic.NodeType.TOPIC);

    assertTrue(
        topics.stream()
            .allMatch(
                topic ->
                    childrenOf(topic).stream()
                        .anyMatch(
                            child -> child.getNodeType() == SyllabusTopic.NodeType.SUBTOPIC)));
    assertEquals(
        List.of("SCI_P5_SYSTEMS_HUMAN_RESPIRATORY_CIRCULATORY"),
        codes(
            childrenOf(topic("SCI_P5_SYSTEMS_HUMAN")).stream()
                .filter(child -> "Respiratory and circulatory systems".equals(child.getName()))
                .toList()));
    assertFalse(topic("SCI_P5_SYSTEMS_PLANT_RESPIRATORY_CIRCULATORY").isActive());
    assertFalse(
        childrenOf(topic("SCI_P5_SYSTEMS_PLANT")).stream()
            .anyMatch(child -> "Respiratory and circulatory systems".equals(child.getName())));
  }

  @Test
  void everySeededNodeHasNonblankCurriculumAndSourceMetadata() {
    Integer blankMetadataRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM syllabus_topics "
                + "WHERE curriculum_version IS NULL OR TRIM(curriculum_version) = '' "
                + "OR source_reference IS NULL OR TRIM(source_reference) = ''",
            Integer.class);

    assertEquals(0, blankMetadataRows);
    assertEquals(
        28, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM syllabus_topics", Integer.class));
  }

  @Test
  void storesEveryParentAtTheRequiredDepth() {
    SyllabusTopic p5 = topic("SCI_P5");
    SyllabusTopic cycles = topic("SCI_P5_CYCLES");
    SyllabusTopic reproduction = topic("SCI_P5_CYCLES_PLANTS_ANIMALS_REPRODUCTION");
    SyllabusTopic reproductionTopic = topic("SCI_P5_CYCLES_PLANTS_ANIMALS");

    assertEquals(topic("SCI").getId(), p5.getParentId());
    assertEquals(p5.getId(), cycles.getParentId());
    assertEquals(reproductionTopic.getId(), reproduction.getParentId());
    assertEquals(Short.valueOf((short) 3), reproduction.getParentDepth());
    assertEquals(SyllabusTopic.NodeType.SUBTOPIC, reproduction.getNodeType());
  }

  @Test
  void rejectsDuplicateStableCodes() {
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO syllabus_topics "
                    + "(code, name, node_type, depth, sort_order, curriculum_version, "
                    + "source_reference) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "SCI",
                "Duplicate Science",
                "SUBJECT",
                0,
                20,
                VERSION,
                "https://www.moe.gov.sg/"));
  }

  @Test
  void rejectsCyclesAndSkippedHierarchyLevels() {
    SyllabusTopic p5 = topic("SCI_P5");
    SyllabusTopic descendant = topic("SCI_P5_SYSTEMS_ELECTRICAL");

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "UPDATE syllabus_topics SET parent_id = ?, parent_depth = ? WHERE id = ?",
                descendant.getId(),
                descendant.getDepth(),
                p5.getId()));
  }

  @Test
  void returnsSiblingsInDeterministicCurriculumOrder() {
    assertEquals(List.of("Cycles", "Systems"), names(childrenOf(topic("SCI_P5"))));
    assertEquals(
        List.of("Frictional force", "Gravitational force", "Elastic spring force"),
        names(childrenOf(topic("SCI_P6_INTERACTIONS_FORCES"))));
  }

  @Test
  void repositoryExposesNoTaxonomyMutationMethods() {
    assertFalse(
        java.util.Arrays.stream(SyllabusTopicRepository.class.getMethods())
            .anyMatch(
                method ->
                    method.getName().startsWith("save") || method.getName().startsWith("delete")));
  }

  private SyllabusTopic topic(String code) {
    return repository.findByCode(code).orElseThrow();
  }

  private List<SyllabusTopic> childrenOf(SyllabusTopic parent) {
    return repository.findAllByParentIdAndActiveTrueOrderBySortOrderAscCodeAsc(parent.getId());
  }

  private List<String> codes(List<SyllabusTopic> topics) {
    return topics.stream().map(SyllabusTopic::getCode).toList();
  }

  private List<String> names(List<SyllabusTopic> topics) {
    return topics.stream().map(SyllabusTopic::getName).toList();
  }

  private Map<SyllabusTopic.NodeType, Long> activeNodeCounts() {
    return repository.findAllByActiveTrueOrderByDepthAscSortOrderAscCodeAsc().stream()
        .collect(
            java.util.stream.Collectors.groupingBy(
                SyllabusTopic::getNodeType, java.util.stream.Collectors.counting()));
  }
}
