package com.fttranscendence.learning.question;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends Repository<Question, Long> {

  <S extends Question> S save(S question);

  Optional<Question> findByCode(String code);

  Optional<Question> findById(Long id);

  @Query(
      """
        select distinct question from Question question
        join fetch question.syllabusTopic topic
        left join fetch question.images image
        order by question.id asc
        """)
  List<Question> findAllForDuplicateComparison();

  boolean existsByCode(String code);

  @Query(
      """
        select case when count(worksheetQuestion) > 0 then true else false end
        from WorksheetQuestion worksheetQuestion
        where worksheetQuestion.question.id = :questionId
        """)
  boolean isUsedByAnyWorksheet(@Param("questionId") Long questionId);

  @Query(
      """
        SELECT question
        FROM Question question
        WHERE question.syllabusTopic.id = :syllabusTopicId
          AND question.archiveState = :archiveState
        ORDER BY question.code ASC
        """)
  List<Question> findAllBySyllabusTopicAndArchiveState(
      @Param("syllabusTopicId") Long syllabusTopicId,
      @Param("archiveState") Question.ArchiveState archiveState);

  @Query(
      """
        select question from Question question
        join fetch question.syllabusTopic topic
        where topic.id in :topicIds
          and question.archiveState = com.fttranscendence.learning.question.Question.ArchiveState.ACTIVE
          and (:questionType is null or question.questionType = :questionType)
          and (:difficulty is null or question.difficulty = :difficulty)
        order by topic.id asc, question.code asc, question.id asc
        """)
  List<Question> findDeterministicActiveQuestionBank(
      @Param("topicIds") List<Long> topicIds,
      @Param("questionType") Question.QuestionType questionType,
      @Param("difficulty") Question.Difficulty difficulty);

  @Query(
      """
        select question from Question question
        join fetch question.syllabusTopic topic
        where question.archiveState = com.fttranscendence.learning.question.Question.ArchiveState.ACTIVE
          and (:questionType is null or question.questionType = :questionType)
          and (:difficulty is null or question.difficulty = :difficulty)
        order by topic.id asc, question.code asc, question.id asc
        """)
  List<Question> findAllDeterministicActiveQuestionBank(
      @Param("questionType") Question.QuestionType questionType,
      @Param("difficulty") Question.Difficulty difficulty);

  @Query(
      value =
          """
            select question
            from Question question
            join fetch question.syllabusTopic topic
            where (:topicId is null or topic.id = :topicId)
              and (:questionType is null or question.questionType = :questionType)
              and (:difficulty is null or question.difficulty = :difficulty)
              and question.archiveState = :archiveState
              and (
                :search is null
                or cast(function('translate', lower(question.code),
                  'àáâãäåāăąçćčďèéêëēĕėęěìíîïīĭįłñńňòóôõöōŏőřŕśšşťùúûüūŭůűųýÿźžż',
                  'aaaaaaaaacccdeeeeeeeeeiiiiiiilnnnoooooooorrssstuuuuuuuuuyyzzz') as string) like :search escape '!'
                or cast(function('translate', lower(question.prompt),
                  'àáâãäåāăąçćčďèéêëēĕėęěìíîïīĭįłñńňòóôõöōŏőřŕśšşťùúûüūŭůűųýÿźžż',
                  'aaaaaaaaacccdeeeeeeeeeiiiiiiilnnnoooooooorrssstuuuuuuuuuyyzzz') as string) like :search escape '!'
                or exists (
                  select 1 from Question keywordQuestion join keywordQuestion.keywords keyword
                  where keywordQuestion = question
                    and cast(function('translate', lower(keyword),
                      'àáâãäåāăąçćčďèéêëēĕėęěìíîïīĭįłñńňòóôõöōŏőřŕśšşťùúûüūŭůűųýÿźžż',
                      'aaaaaaaaacccdeeeeeeeeeiiiiiiilnnnoooooooorrssstuuuuuuuuuyyzzz') as string) like :search escape '!'
                )
              )
            order by question.code asc, question.id asc
            """,
      countQuery =
          """
            select count(question)
            from Question question
            where (:topicId is null or question.syllabusTopic.id = :topicId)
              and (:questionType is null or question.questionType = :questionType)
              and (:difficulty is null or question.difficulty = :difficulty)
              and question.archiveState = :archiveState
              and (
                :search is null
                or cast(function('translate', lower(question.code),
                  'àáâãäåāăąçćčďèéêëēĕėęěìíîïīĭįłñńňòóôõöōŏőřŕśšşťùúûüūŭůűųýÿźžż',
                  'aaaaaaaaacccdeeeeeeeeeiiiiiiilnnnoooooooorrssstuuuuuuuuuyyzzz') as string) like :search escape '!'
                or cast(function('translate', lower(question.prompt),
                  'àáâãäåāăąçćčďèéêëēĕėęěìíîïīĭįłñńňòóôõöōŏőřŕśšşťùúûüūŭůűųýÿźžż',
                  'aaaaaaaaacccdeeeeeeeeeiiiiiiilnnnoooooooorrssstuuuuuuuuuyyzzz') as string) like :search escape '!'
                or exists (
                  select 1 from Question keywordQuestion join keywordQuestion.keywords keyword
                  where keywordQuestion = question
                    and cast(function('translate', lower(keyword),
                      'àáâãäåāăąçćčďèéêëēĕėęěìíîïīĭįłñńňòóôõöōŏőřŕśšşťùúûüūŭůűųýÿźžż',
                      'aaaaaaaaacccdeeeeeeeeeiiiiiiilnnnoooooooorrssstuuuuuuuuuyyzzz') as string) like :search escape '!'
                )
              )
            """)
  Page<Question> findQuestionBank(
      @Param("topicId") Long topicId,
      @Param("questionType") Question.QuestionType questionType,
      @Param("difficulty") Question.Difficulty difficulty,
      @Param("archiveState") Question.ArchiveState archiveState,
      @Param("search") String search,
      Pageable pageable);
}
