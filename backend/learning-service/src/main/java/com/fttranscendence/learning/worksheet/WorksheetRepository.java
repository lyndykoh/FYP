package com.fttranscendence.learning.worksheet;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface WorksheetRepository extends Repository<Worksheet, Long> {

  <S extends Worksheet> S save(S worksheet);

  Optional<Worksheet> findById(Long id);

  Optional<Worksheet> findByIdAndTutorId(Long id, Long tutorId);

  Optional<Worksheet> findByTutorIdAndCode(Long tutorId, String code);

  Optional<Worksheet> findByGenerationRequest_IdAndTutorId(Long generationRequestId, Long tutorId);

  List<Worksheet> findAllByTutorIdAndStatusOrderByTitleAsc(Long tutorId, Worksheet.Status status);

  @Query(
      """
        SELECT DISTINCT worksheet
        FROM Worksheet worksheet
        LEFT JOIN FETCH worksheet.assignments
        WHERE worksheet.tutorId = :tutorId
        ORDER BY worksheet.title ASC, worksheet.id ASC
        """)
  List<Worksheet> findAllByTutorIdWithAssignments(@Param("tutorId") Long tutorId);

  @Query(
      """
        SELECT DISTINCT worksheet
        FROM Worksheet worksheet
        JOIN worksheet.assignments assignment
        WHERE assignment.assignmentType = :assignmentType
          AND assignment.targetId = :targetId
        ORDER BY worksheet.title ASC
        """)
  List<Worksheet> findAssignedWorksheets(
      @Param("assignmentType") Worksheet.AudienceType assignmentType,
      @Param("targetId") Long targetId);

  @Query(
      """
        SELECT DISTINCT worksheet
        FROM Worksheet worksheet
        JOIN FETCH worksheet.assignments assignment
        WHERE worksheet.tutorId = :tutorId
          AND assignment.assignmentType = com.fttranscendence.learning.worksheet.Worksheet.AudienceType.CLASS
          AND assignment.targetId = :classId
        ORDER BY worksheet.title ASC, worksheet.id ASC
        """)
  List<Worksheet> findClassAssignedWorksheetsByTutorId(
      @Param("tutorId") Long tutorId, @Param("classId") Long classId);

  @Query(
      """
        SELECT DISTINCT worksheet
        FROM Worksheet worksheet
        JOIN FETCH worksheet.assignments assignment
        WHERE worksheet.tutorId = :tutorId
          AND worksheet.status = com.fttranscendence.learning.worksheet.Worksheet.Status.APPROVED
          AND assignment.assignmentType = com.fttranscendence.learning.worksheet.Worksheet.AudienceType.STUDENT
          AND assignment.studentProfileId = :studentProfileId
        ORDER BY worksheet.title ASC, worksheet.id ASC
        """)
  List<Worksheet> findApprovedStudentAssignedWorksheetsByTutorId(
      @Param("tutorId") Long tutorId, @Param("studentProfileId") Long studentProfileId);

  /**
   * Student visibility is derived from their own direct assignment or active class membership. No
   * caller-controlled profile identifier is involved.
   */
  @Query(
      """
        SELECT DISTINCT worksheet
        FROM Worksheet worksheet
        JOIN FETCH worksheet.questions worksheetQuestion
        JOIN FETCH worksheetQuestion.question question
        JOIN FETCH question.syllabusTopic topic
        JOIN worksheet.assignments assignment
        WHERE worksheet.status = com.fttranscendence.learning.worksheet.Worksheet.Status.APPROVED
          AND (
            (assignment.assignmentType = com.fttranscendence.learning.worksheet.Worksheet.AudienceType.STUDENT
             AND assignment.studentProfileId = :studentProfileId)
            OR
            (assignment.assignmentType = com.fttranscendence.learning.worksheet.Worksheet.AudienceType.CLASS
             AND EXISTS (
                SELECT membership.id FROM ClassMembership membership
                WHERE membership.studentProfile.id = :studentProfileId
                  AND membership.tutorId = worksheet.tutorId
                  AND membership.classId = assignment.classId
             ))
          )
        ORDER BY worksheet.approvedAt DESC, worksheet.id DESC
        """)
  List<Worksheet> findApprovedAssignedToStudentWithQuestions(
      @Param("studentProfileId") Long studentProfileId);
}
