package com.fttranscendence.learning.security;

import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the minimal self profile every authenticated Student requires. Tutor ownership and class
 * membership remain unset until a Tutor enrolls them.
 */
@Service
public class StudentProfileProvisioningService {
  private final StudentProfileRepository students;
  private final EntityManager entityManager;

  public StudentProfileProvisioningService(
      StudentProfileRepository students, EntityManager entityManager) {
    this.students = students;
    this.entityManager = entityManager;
  }

  @Transactional
  public void ensureProfile(AuthenticatedUser user) {
    if (user == null
        || user.userId() <= 0
        || !"STUDENT".equals(user.role())
        || students.findByLoginUserId(user.userId()).isPresent()) return;
    StudentProfile profile = new StudentProfile();
    profile.setLoginUserId(user.userId());
    profile.setFullName(
        user.fullName() == null || user.fullName().isBlank() ? user.email() : user.fullName());
    try {
      students.save(profile);
      entityManager.flush();
    } catch (DataIntegrityViolationException ignored) {
      // Concurrent first requests race on the unique login identity; the
      // winning transaction already created the same self profile.
    }
  }
}
