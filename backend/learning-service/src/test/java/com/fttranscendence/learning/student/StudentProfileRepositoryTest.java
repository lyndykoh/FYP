package com.fttranscendence.learning.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StudentProfileRepositoryTest {

  @Autowired private StudentProfileRepository repository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Validator validator;

  @BeforeEach
  void clearLearningData() {
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
    jdbcTemplate.update("DELETE FROM tutor_classes");
  }

  @Test
  void persistsOneCanonicalProfileWithAnOptionalLoginAndSeveralClasses() {
    long firstClassId = insertClass(101L, "P5 Science A", "p5 science a");
    long secondClassId = insertClass(101L, "P5 Science B", "p5 science b");

    StudentProfile unlinked = repository.save(profileFor(101L, null, "Unlinked Student"));
    StudentProfile linked = profileFor(101L, 9001L, "  Test Student  ");
    linked.addClassMembership(firstClassId);
    linked.addClassMembership(secondClassId);
    linked = repository.save(linked);
    entityManager.flush();
    entityManager.clear();

    StudentProfile loaded = repository.findByLoginUserId(9001L).orElseThrow();

    assertEquals(linked.getId(), loaded.getId());
    assertEquals("Test Student", loaded.getFullName());
    assertEquals(2, loaded.getMemberships().size());
    assertTrue(repository.findByIdAndTutorId(loaded.getId(), 101L).isPresent());
    assertTrue(repository.findByIdAndTutorId(loaded.getId(), 202L).isEmpty());
    assertNull(
        repository.findByIdAndTutorId(unlinked.getId(), 101L).orElseThrow().getLoginUserId());
    assertEquals(2, repository.findAllByTutorIdOrderByFullNameAsc(101L).size());
  }

  @Test
  void allowsAnUnassignedProfileWhileValidatingRemainingIdentityFields() {
    StudentProfile missingOwner = profileFor(null, null, "Test Student");
    StudentProfile invalidOwner = profileFor(0L, null, "Test Student");
    StudentProfile missingName = profileFor(101L, null, "  ");
    StudentProfile invalidLogin = profileFor(101L, 0L, "Test Student");
    StudentProfile invalidMembership = profileFor(101L, null, "Test Student");
    invalidMembership.addClassMembership(null);
    StudentProfile unlinked = profileFor(101L, null, "Test Student");

    assertTrue(validator.validate(missingOwner).isEmpty());
    assertFalse(validator.validate(invalidOwner).isEmpty());
    assertFalse(validator.validate(missingName).isEmpty());
    assertFalse(validator.validate(invalidLogin).isEmpty());
    assertFalse(validator.validate(invalidMembership).isEmpty());
    assertTrue(validator.validate(unlinked).isEmpty());
  }

  @Test
  void preventsTwoProfilesFromLinkingToTheSameLoginIdentity() {
    repository.save(profileFor(101L, 9001L, "First Profile"));
    entityManager.flush();

    assertTrue(repository.existsByLoginUserId(9001L));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          repository.save(profileFor(202L, 9001L, "Duplicate Link"));
          entityManager.flush();
        });
  }

  @Test
  void preventsDuplicateMemberships() {
    long classId = insertClass(101L, "P5 Science", "p5 science");
    StudentProfile student = repository.save(profileFor(101L, null, "Test Student"));
    entityManager.flush();

    insertMembership(student.getId(), classId, 101L);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertMembership(student.getId(), classId, 101L));
  }

  @Test
  void preventsCrossTutorClassMembership() {
    long otherTutorsClassId = insertClass(202L, "Other Tutor Class", "other tutor class");
    StudentProfile student = repository.save(profileFor(101L, null, "Test Student"));
    entityManager.flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertMembership(student.getId(), otherTutorsClassId, 101L));
  }

  @Test
  void cascadesMembershipsOnlyWhenAProfileIsRemovedAndRestrictsClassDeletion() {
    long classId = insertClass(101L, "P5 Science", "p5 science");
    StudentProfile student = repository.save(profileFor(101L, null, "Test Student"));
    entityManager.flush();
    insertMembership(student.getId(), classId, 101L);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbcTemplate.update("DELETE FROM tutor_classes WHERE id = ?", classId));

    jdbcTemplate.update("DELETE FROM student_profiles WHERE id = ?", student.getId());

    assertEquals(0, count("class_memberships"));
    assertEquals(1, count("tutor_classes"));
  }

  @Test
  void exposesNoUnscopedHardDeleteOperation() {
    assertFalse(
        Arrays.stream(StudentProfileRepository.class.getMethods())
            .anyMatch(method -> method.getName().startsWith("delete")));
  }

  private StudentProfile profileFor(Long tutorId, Long loginUserId, String fullName) {
    StudentProfile profile = new StudentProfile();
    profile.setTutorId(tutorId);
    profile.setLoginUserId(loginUserId);
    profile.setFullName(fullName);
    return profile;
  }

  private long insertClass(long tutorId, String className, String normalizedClassName) {
    jdbcTemplate.update(
        "INSERT INTO tutor_classes "
            + "(tutor_id, class_name, normalized_class_name, subject, class_level, status) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        tutorId,
        className,
        normalizedClassName,
        "Science",
        "P5",
        "ACTIVE");
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tutor_classes WHERE tutor_id = ? AND normalized_class_name = ?",
            Long.class,
            tutorId,
            normalizedClassName);
    if (id == null) {
      throw new IllegalStateException("Inserted class was not found");
    }
    return id;
  }

  private void insertMembership(long studentProfileId, long classId, long tutorId) {
    jdbcTemplate.update(
        "INSERT INTO class_memberships (student_profile_id, class_id, tutor_id) "
            + "VALUES (?, ?, ?)",
        studentProfileId,
        classId,
        tutorId);
  }

  private int count(String tableName) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    return count == null ? 0 : count;
  }
}
