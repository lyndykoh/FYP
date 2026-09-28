package com.fttranscendence.learning.mastery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MasteryRecordRepository extends Repository<MasteryRecord, Long> {

  <S extends MasteryRecord> S save(S record);

  Optional<MasteryRecord> findByStudentProfileIdAndSyllabusTopicId(
      Long studentProfileId, Long syllabusTopicId);

  /**
   * Used by the non-transactional HTTP topic-detail projection. History is deliberately fetched
   * here because Open Session in View is disabled.
   */
  @Query(
      """
        select distinct mastery
        from MasteryRecord mastery
        left join fetch mastery.history history
        where mastery.studentProfile.id = :studentProfileId
          and mastery.syllabusTopic.id = :syllabusTopicId
        """)
  Optional<MasteryRecord> findByStudentProfileIdAndSyllabusTopicIdWithHistory(
      @Param("studentProfileId") Long studentProfileId,
      @Param("syllabusTopicId") Long syllabusTopicId);

  List<MasteryRecord> findAllByStudentProfileIdOrderByScoreDesc(Long studentProfileId);

  @Query(
      """
        select distinct mastery
        from MasteryRecord mastery
        join fetch mastery.syllabusTopic topic
        left join fetch mastery.history history
        where mastery.studentProfile.id = :studentProfileId
        order by topic.name asc, topic.id asc
        """)
  List<MasteryRecord> findProfileRecordsByStudentProfileIdWithTopicAndHistory(
      @Param("studentProfileId") Long studentProfileId);

  @Query(
      """
        select mastery
        from MasteryRecord mastery
        join fetch mastery.syllabusTopic topic
        where mastery.studentProfile.id in :studentProfileIds
        order by mastery.studentProfile.id asc, topic.name asc, topic.id asc
        """)
  List<MasteryRecord> findAllByStudentProfileIdInWithTopic(
      @Param("studentProfileIds") List<Long> studentProfileIds);
}
