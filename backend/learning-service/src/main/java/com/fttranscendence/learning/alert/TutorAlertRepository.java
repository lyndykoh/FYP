package com.fttranscendence.learning.alert;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface TutorAlertRepository extends Repository<TutorAlert, Long> {

  <S extends TutorAlert> S save(S alert);

  Optional<TutorAlert> findByIdAndTutorId(Long id, Long tutorId);

  Optional<TutorAlert> findByTutorIdAndDeduplicationKey(Long tutorId, String deduplicationKey);

  List<TutorAlert> findAllByTutorIdAndAlertStatusOrderByCreatedAtDesc(
      Long tutorId, TutorAlert.AlertStatus alertStatus);

  List<TutorAlert> findAllByTutorIdAndAlertStatusInOrderByCreatedAtDescIdDesc(
      Long tutorId, List<TutorAlert.AlertStatus> statuses);

  @org.springframework.data.jpa.repository.Query(
      """
        select count(distinct alert.studentProfile.id)
        from TutorAlert alert
        where alert.tutorId = :tutorId
          and alert.alertStatus in :statuses
        """)
  long countDistinctActiveStudentsByTutorId(
      @org.springframework.data.repository.query.Param("tutorId") Long tutorId,
      @org.springframework.data.repository.query.Param("statuses")
          List<TutorAlert.AlertStatus> statuses);

  @org.springframework.data.jpa.repository.Query(
      """
        select alert from TutorAlert alert
        where alert.tutorId = :tutorId
          and alert.studentProfile.id = :studentProfileId
          and alert.alertStatus in :statuses
        order by alert.createdAt desc, alert.id desc
        """)
  List<TutorAlert> findActiveByTutorIdAndStudentProfileId(
      @org.springframework.data.repository.query.Param("tutorId") Long tutorId,
      @org.springframework.data.repository.query.Param("studentProfileId") Long studentProfileId,
      @org.springframework.data.repository.query.Param("statuses")
          List<TutorAlert.AlertStatus> statuses);
}
