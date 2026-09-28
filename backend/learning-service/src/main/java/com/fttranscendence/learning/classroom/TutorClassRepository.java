package com.fttranscendence.learning.classroom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TutorClassRepository extends Repository<TutorClass, Long> {

  <S extends TutorClass> S save(S tutorClass);

  Optional<TutorClass> findById(Long id);

  Optional<TutorClass> findByIdAndTutorId(Long id, Long tutorId);

  List<TutorClass> findAllByTutorIdAndStatusOrderByClassNameAsc(
      Long tutorId, TutorClass.Status status);

  List<TutorClass> findAllByTutorIdOrderByClassNameAsc(Long tutorId);

  boolean existsByTutorIdAndClassNameIgnoreCase(Long tutorId, String className);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      """
        update TutorClass tutorClass
           set tutorClass.status = com.fttranscendence.learning.classroom.TutorClass.Status.INACTIVE
         where tutorClass.id = :classId
           and tutorClass.tutorId = :tutorId
        """)
  int deactivateOwnedClass(@Param("classId") Long classId, @Param("tutorId") Long tutorId);
}
