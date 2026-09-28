package com.fttranscendence.learning.student;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface TutorNoteRepository extends Repository<TutorNote, Long> {

  <S extends TutorNote> S save(S note);

  void delete(TutorNote note);

  List<TutorNote> findAllByTutorIdAndStudentProfileIdOrderByUpdatedAtDescIdDesc(
      Long tutorId, Long studentProfileId);

  Optional<TutorNote> findByIdAndTutorIdAndStudentProfileId(
      Long id, Long tutorId, Long studentProfileId);
}
