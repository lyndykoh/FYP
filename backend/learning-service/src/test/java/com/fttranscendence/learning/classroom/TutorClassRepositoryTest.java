package com.fttranscendence.learning.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
class TutorClassRepositoryTest {

  @Autowired private TutorClassRepository repository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Validator validator;

  @BeforeEach
  void clearClasses() {
    jdbcTemplate.update("DELETE FROM tutor_classes");
  }

  @Test
  void persistsRequiredFieldsSchedulesAndOwnerScopedQueries() {
    TutorClass firstTutorClass = repository.save(classFor(101L, "P5 Science"));
    TutorClass secondTutorClass = repository.save(classFor(202L, "P5 Science"));
    entityManager.flush();
    entityManager.clear();

    TutorClass loaded = repository.findByIdAndTutorId(firstTutorClass.getId(), 101L).orElseThrow();

    assertEquals("P5 Science", loaded.getClassName());
    assertEquals("Science", loaded.getSubject());
    assertEquals("P5", loaded.getLevel());
    assertEquals(TutorClass.Status.ACTIVE, loaded.getStatus());
    assertEquals(1, loaded.getSchedules().size());
    assertTrue(repository.findByIdAndTutorId(firstTutorClass.getId(), 202L).isEmpty());
    assertTrue(repository.findByIdAndTutorId(secondTutorClass.getId(), 202L).isPresent());
  }

  @Test
  void rejectsMissingRequiredFieldsAndInvalidOwnerIds() {
    TutorClass missingName = classFor(101L, "P5 Science");
    missingName.setClassName("  ");
    TutorClass missingSubject = classFor(101L, "P5 Science");
    missingSubject.setSubject(null);
    TutorClass missingLevel = classFor(101L, "P5 Science");
    missingLevel.setLevel(" ");
    TutorClass missingOwner = classFor(null, "P5 Science");
    TutorClass invalidOwner = classFor(0L, "P5 Science");

    assertFalse(validator.validate(missingName).isEmpty());
    assertFalse(validator.validate(missingSubject).isEmpty());
    assertFalse(validator.validate(missingLevel).isEmpty());
    assertFalse(validator.validate(missingOwner).isEmpty());
    assertFalse(validator.validate(invalidOwner).isEmpty());
  }

  @Test
  void rejectsDuplicateClassNamesForTheSameTutorIgnoringCaseAndWhitespace() {
    repository.save(classFor(101L, "P5 Science"));
    entityManager.flush();

    assertTrue(repository.existsByTutorIdAndClassNameIgnoreCase(101L, "p5 science"));
    assertFalse(repository.existsByTutorIdAndClassNameIgnoreCase(202L, "p5 science"));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          repository.save(classFor(101L, "  p5 SCIENCE  "));
          entityManager.flush();
        });
  }

  @Test
  void supportsInactiveClassesAndOwnerScopedSoftDeletion() {
    TutorClass tutorClass = repository.save(classFor(101L, "P6 Science"));
    entityManager.flush();

    assertEquals(0, repository.deactivateOwnedClass(tutorClass.getId(), 202L));
    assertEquals(1, repository.deactivateOwnedClass(tutorClass.getId(), 101L));

    TutorClass inactive = repository.findByIdAndTutorId(tutorClass.getId(), 101L).orElseThrow();
    assertEquals(TutorClass.Status.INACTIVE, inactive.getStatus());
    assertEquals(1, inactive.getSchedules().size());
    assertEquals(
        1,
        repository
            .findAllByTutorIdAndStatusOrderByClassNameAsc(101L, TutorClass.Status.INACTIVE)
            .size());
  }

  @Test
  void exposesNoUnscopedHardDeleteOperation() {
    assertFalse(
        Arrays.stream(TutorClassRepository.class.getMethods())
            .anyMatch(method -> method.getName().startsWith("delete")));
  }

  private TutorClass classFor(Long tutorId, String className) {
    TutorClass tutorClass = new TutorClass();
    tutorClass.setTutorId(tutorId);
    tutorClass.setClassName(className);
    tutorClass.setSubject("Science");
    tutorClass.setLevel("P5");
    tutorClass
        .getSchedules()
        .add(
            new TutorClass.ScheduleSlot(
                DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(16, 30)));
    return tutorClass;
  }
}
