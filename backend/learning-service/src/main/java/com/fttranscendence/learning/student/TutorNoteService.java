package com.fttranscendence.learning.student;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TutorNoteService {

  private final StudentProfileRepository students;
  private final TutorNoteRepository notes;
  private final EntityManager entityManager;

  public TutorNoteService(
      StudentProfileRepository students, TutorNoteRepository notes, EntityManager entityManager) {
    this.students = students;
    this.notes = notes;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public List<TutorNoteRequest.Response> list(long tutorId, long studentId) {
    StudentProfile student = requireOwnedStudent(tutorId, studentId);
    return notes
        .findAllByTutorIdAndStudentProfileIdOrderByUpdatedAtDescIdDesc(tutorId, student.getId())
        .stream()
        .map(TutorNoteRequest.Response::from)
        .toList();
  }

  @Transactional
  public TutorNoteRequest.Response create(long tutorId, long studentId, TutorNoteRequest request) {
    StudentProfile student = requireOwnedStudent(tutorId, studentId);
    try {
      TutorNote saved = notes.save(new TutorNote(tutorId, student, request.content()));
      entityManager.flush();
      return TutorNoteRequest.Response.from(saved);
    } catch (DataAccessException exception) {
      throw new TutorNotePersistenceException(exception);
    }
  }

  @Transactional
  public TutorNoteRequest.Response update(
      long tutorId, long studentId, long noteId, TutorNoteRequest request) {
    requireOwnedStudent(tutorId, studentId);
    TutorNote note =
        notes
            .findByIdAndTutorIdAndStudentProfileId(noteId, tutorId, studentId)
            .orElseThrow(TutorNoteNotFoundException::new);
    try {
      note.updateContent(request.content());
      entityManager.flush();
      return TutorNoteRequest.Response.from(note);
    } catch (DataAccessException exception) {
      throw new TutorNotePersistenceException(exception);
    }
  }

  @Transactional
  public void delete(long tutorId, long studentId, long noteId) {
    requireOwnedStudent(tutorId, studentId);
    TutorNote note =
        notes
            .findByIdAndTutorIdAndStudentProfileId(noteId, tutorId, studentId)
            .orElseThrow(TutorNoteNotFoundException::new);
    try {
      notes.delete(note);
      entityManager.flush();
    } catch (DataAccessException exception) {
      throw new TutorNotePersistenceException(exception);
    }
  }

  private StudentProfile requireOwnedStudent(long tutorId, long studentId) {
    if (tutorId <= 0)
      throw new StudentService.InvalidStudentRequestException(
          "Authenticated tutor id must be positive");
    return students
        .findByIdAndTutorId(studentId, tutorId)
        .orElseThrow(() -> new StudentService.StudentNotFoundException(studentId));
  }

  public static class TutorNoteNotFoundException extends RuntimeException {
    public TutorNoteNotFoundException() {
      super("Tutor note was not found");
    }
  }

  public static class TutorNotePersistenceException extends RuntimeException {
    public TutorNotePersistenceException(Throwable cause) {
      super("Tutor note could not be saved", cause);
    }
  }
}
