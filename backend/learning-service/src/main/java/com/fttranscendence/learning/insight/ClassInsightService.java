package com.fttranscendence.learning.insight;

import com.fttranscendence.learning.classroom.ClassService;
import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes deterministic, aggregate-only insight snapshots. This is deliberately independent of
 * any AI provider: future AI text can be attached to this evidence but must not replace it or
 * change student mastery.
 */
@Service
public class ClassInsightService {
  private static final BigDecimal DEFAULT_AVERAGE = new BigDecimal("70.00");
  private static final BigDecimal DEFAULT_RATIO = new BigDecimal("40.00");
  private static final int DEFAULT_MINIMUM = 3;
  private final TutorClassRepository classes;
  private final SyllabusTopicRepository topics;
  private final StudentProfileRepository students;
  private final MasteryRecordRepository mastery;
  private final JdbcTemplate jdbc;

  public ClassInsightService(
      TutorClassRepository classes,
      SyllabusTopicRepository topics,
      StudentProfileRepository students,
      MasteryRecordRepository mastery,
      JdbcTemplate jdbc) {
    this.classes = classes;
    this.topics = topics;
    this.students = students;
    this.mastery = mastery;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<CoveredTopic> coveredTopics(long tutorId, long classId) {
    requireOwned(tutorId, classId);
    return jdbc.query(
        """
            select topic.id, topic.code, topic.name, topic.node_type from class_topic_coverage coverage
            join syllabus_topics topic on topic.id = coverage.topic_id
            where coverage.class_id = ? order by topic.sort_order, topic.code
            """,
        (rs, row) ->
            new CoveredTopic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
        classId);
  }

  @Transactional
  public List<CoveredTopic> replaceCoveredTopics(
      long tutorId, long classId, List<Long> requestedIds) {
    requireOwned(tutorId, classId);
    List<Long> ids =
        requestedIds == null ? List.of() : requestedIds.stream().distinct().sorted().toList();
    List<SyllabusTopic> selected = ids.isEmpty() ? List.of() : topics.findAllById(ids);
    if (selected.size() != ids.size()
        || selected.stream()
            .anyMatch(
                topic ->
                    !topic.isActive()
                        || (topic.getNodeType() != SyllabusTopic.NodeType.TOPIC
                            && topic.getNodeType() != SyllabusTopic.NodeType.SUBTOPIC))) {
      throw new InvalidInsightRequestException(
          "Covered topics must be active TOPIC or SUBTOPIC ids");
    }
    jdbc.update("delete from class_topic_coverage where class_id = ?", classId);
    for (Long topicId : ids)
      jdbc.update(
          "insert into class_topic_coverage (class_id, topic_id) values (?, ?)", classId, topicId);
    requestRefresh(tutorId, classId);
    return coveredTopics(tutorId, classId);
  }

  @Transactional(readOnly = true)
  public Settings settings(long tutorId, long classId) {
    requireOwned(tutorId, classId);
    List<Settings> results =
        jdbc.query(
            "select weak_average_mastery_percent, weak_student_ratio_percent, minimum_active_students from class_insight_settings where class_id = ?",
            (rs, row) -> new Settings(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getInt(3)),
            classId);
    return results.isEmpty()
        ? new Settings(DEFAULT_AVERAGE, DEFAULT_RATIO, DEFAULT_MINIMUM)
        : results.get(0);
  }

  @Transactional
  public Settings updateSettings(long tutorId, long classId, Settings settings) {
    requireOwned(tutorId, classId);
    int changed =
        jdbc.update(
            "update class_insight_settings set weak_average_mastery_percent = ?, weak_student_ratio_percent = ?, minimum_active_students = ?, updated_at = current_timestamp where class_id = ?",
            settings.weakAverageMasteryPercent(),
            settings.weakStudentRatioPercent(),
            settings.minimumActiveStudents(),
            classId);
    if (changed == 0)
      jdbc.update(
          "insert into class_insight_settings (class_id, weak_average_mastery_percent, weak_student_ratio_percent, minimum_active_students, updated_at) values (?, ?, ?, ?, current_timestamp)",
          classId,
          settings.weakAverageMasteryPercent(),
          settings.weakStudentRatioPercent(),
          settings.minimumActiveStudents());
    requestRefresh(tutorId, classId);
    return settings;
  }

  /**
   * Reads the most recent snapshot and atomically records a refresh request when the snapshot is
   * absent or stale. Class detail is intentionally a read-only query, so this method must not join
   * that transaction: PostgreSQL rejects queue DML after a connection has been marked read-only.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ClassInsightResponse insights(long tutorId, long classId) {
    requireOwned(tutorId, classId);
    requestRefreshIfMissingOrStale(tutorId, classId);
    Snapshot snapshot = latestSnapshot(tutorId, classId);
    QueueStatus queue = queueStatus(classId);
    if (snapshot == null)
      return new ClassInsightResponse(
          queue == QueueStatus.FAILED
              ? ClassInsightResponse.Status.FAILED
              : ClassInsightResponse.Status.REFRESHING,
          queue == QueueStatus.FAILED
              ? "Insight refresh failed; retry is queued"
              : "Insights are being refreshed",
          null,
          List.of(),
          List.of());
    String current = fingerprint(tutorId, classId);
    ClassInsightResponse.Status status =
        snapshot.fingerprint().equals(current) && queue == QueueStatus.NONE
            ? ClassInsightResponse.Status.FRESH
            : queue == QueueStatus.FAILED
                ? ClassInsightResponse.Status.FAILED
                : queue == QueueStatus.NONE
                    ? ClassInsightResponse.Status.STALE
                    : ClassInsightResponse.Status.REFRESHING;
    String message =
        status == ClassInsightResponse.Status.FRESH
            ? "Insights are current"
            : "Insight data is refreshing";
    return new ClassInsightResponse(
        status,
        message,
        snapshot.dataAsOf(),
        items(snapshot.id(), classId),
        feedback(snapshot.id(), tutorId));
  }

  @Transactional
  public void addFeedback(long tutorId, long snapshotId, String message) {
    Long classId =
        jdbc.query(
            "select class_id from class_insight_snapshots where id = ? and tutor_id = ?",
            rs -> rs.next() ? rs.getLong(1) : null,
            snapshotId,
            tutorId);
    if (classId == null) throw new ClassService.ClassNotFoundException(snapshotId);
    jdbc.update(
        "insert into class_insight_feedback (snapshot_id, tutor_id, feedback) values (?, ?, ?)",
        snapshotId,
        tutorId,
        message.trim());
  }

  @Transactional
  public void updateRanking(long tutorId, long classId, long topicId, int rank, String note) {
    requireOwned(tutorId, classId);
    if (coveredTopics(tutorId, classId).stream().noneMatch(topic -> topic.id().equals(topicId)))
      throw new ClassService.ClassNotFoundException(classId);
    int changed =
        jdbc.update(
            "update class_insight_ranking_overrides set display_rank = ?, note = ?, updated_at = current_timestamp where class_id = ? and topic_id = ?",
            rank,
            note == null || note.isBlank() ? null : note.trim(),
            classId,
            topicId);
    if (changed == 0)
      jdbc.update(
          "insert into class_insight_ranking_overrides (class_id, tutor_id, topic_id, display_rank, note, updated_at) values (?, ?, ?, ?, ?, current_timestamp)",
          classId,
          tutorId,
          topicId,
          rank,
          note == null || note.isBlank() ? null : note.trim());
  }

  /**
   * Invoked by the scheduler and tests; each queued class is independently transactional at service
   * boundaries.
   */
  @Transactional
  public int runOnce() {
    List<long[]> queued =
        jdbc.query(
            "select class_id, tutor_id from class_insight_refresh_queue where status in ('QUEUED', 'FAILED') order by requested_at asc",
            (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)});
    int completed = 0;
    for (long[] item : queued) {
      jdbc.update(
          "update class_insight_refresh_queue set status = 'RUNNING', started_at = current_timestamp, last_error = null where class_id = ?",
          item[0]);
      try {
        materialize(item[1], item[0]);
        jdbc.update("delete from class_insight_refresh_queue where class_id = ?", item[0]);
        completed++;
      } catch (RuntimeException error) {
        jdbc.update(
            "update class_insight_refresh_queue set status = 'FAILED', completed_at = current_timestamp, last_error = ? where class_id = ?",
            concise(error),
            item[0]);
      }
    }
    return completed;
  }

  private void materialize(long tutorId, long classId) {
    requireOwned(tutorId, classId);
    Settings settings = settings(tutorId, classId);
    List<CoveredTopic> selected = coveredTopics(tutorId, classId);
    List<StudentProfile> members =
        students.findAllByTutorIdAndClassIdOrderByFullNameAsc(tutorId, classId);
    Set<Long> memberIds = members.stream().map(StudentProfile::getId).collect(Collectors.toSet());
    List<MasteryRecord> records =
        memberIds.isEmpty()
            ? List.of()
            : mastery.findAllByStudentProfileIdInWithTopic(new ArrayList<>(memberIds));
    String fingerprint = fingerprint(tutorId, classId);
    jdbc.update(
        "insert into class_insight_snapshots (class_id, tutor_id, fingerprint, status, data_as_of) values (?, ?, ?, 'FRESH', current_timestamp)",
        classId,
        tutorId,
        fingerprint);
    Long snapshotId =
        jdbc.queryForObject(
            "select id from class_insight_snapshots where class_id = ? and tutor_id = ? order by id desc limit 1",
            Long.class,
            classId,
            tutorId);
    Map<Long, List<MasteryRecord>> byTopic =
        records.stream()
            .filter(
                record ->
                    selected.stream()
                        .anyMatch(topic -> topic.id().equals(record.getSyllabusTopic().getId())))
            .collect(Collectors.groupingBy(record -> record.getSyllabusTopic().getId()));
    for (CoveredTopic topic : selected) {
      List<MasteryRecord> topicRecords = byTopic.getOrDefault(topic.id(), List.of());
      BigDecimal total =
          topicRecords.stream()
              .map(MasteryRecord::getScore)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      int assessed =
          (int)
              topicRecords.stream()
                  .map(record -> record.getStudentProfile().getId())
                  .distinct()
                  .count();
      BigDecimal average =
          assessed == 0
              ? BigDecimal.ZERO.setScale(2)
              : total.divide(BigDecimal.valueOf(assessed), 2, RoundingMode.HALF_UP);
      int affected =
          (int)
              members.stream()
                  .filter(
                      student ->
                          topicRecords.stream()
                              .noneMatch(
                                  record ->
                                      record.getStudentProfile().getId().equals(student.getId())
                                          && record
                                                  .getScore()
                                                  .compareTo(settings.weakAverageMasteryPercent())
                                              >= 0))
                  .count();
      boolean weak =
          members.size() >= settings.minimumActiveStudents()
              && (average.compareTo(settings.weakAverageMasteryPercent()) < 0
                  || percent(affected, members.size()).compareTo(settings.weakStudentRatioPercent())
                      >= 0);
      String action =
          weak
              ? "Prioritise guided practice before the next assessment."
              : "Continue monitoring this topic.";
      jdbc.update(
          "insert into class_insight_items (snapshot_id, topic_id, topic_name, average_mastery_percent, active_student_count, assessed_student_count, affected_student_count, weak, suggested_action) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          snapshotId,
          topic.id(),
          topic.name(),
          average,
          members.size(),
          assessed,
          affected,
          weak,
          action);
    }
  }

  private List<ClassInsightResponse.Item> items(long snapshotId, long classId) {
    return jdbc.query(
        """
            select item.topic_id, item.topic_name, item.average_mastery_percent, item.active_student_count, item.assessed_student_count, item.affected_student_count, item.weak, item.suggested_action, rank.display_rank, rank.note
            from class_insight_items item left join class_insight_ranking_overrides rank on rank.class_id = ? and rank.topic_id = item.topic_id
            where item.snapshot_id = ? order by coalesce(rank.display_rank, 2147483647), item.weak desc, item.affected_student_count desc, item.average_mastery_percent asc, item.topic_name asc
            """,
        (rs, row) ->
            new ClassInsightResponse.Item(
                rs.getLong(1),
                rs.getString(2),
                rs.getBigDecimal(3),
                rs.getInt(4),
                rs.getInt(5),
                rs.getInt(6),
                rs.getBoolean(7),
                rs.getString(8),
                (Integer) rs.getObject(9),
                rs.getString(10)),
        classId,
        snapshotId);
  }

  private List<ClassInsightResponse.Feedback> feedback(long snapshotId, long tutorId) {
    return jdbc.query(
        "select id, feedback, created_at from class_insight_feedback where snapshot_id = ? and tutor_id = ? order by created_at asc, id asc",
        (rs, row) ->
            new ClassInsightResponse.Feedback(
                rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()),
        snapshotId,
        tutorId);
  }

  private Snapshot latestSnapshot(long tutorId, long classId) {
    List<Snapshot> snapshots =
        jdbc.query(
            "select id, fingerprint, data_as_of from class_insight_snapshots where tutor_id = ? and class_id = ? order by id desc limit 1",
            (rs, row) ->
                new Snapshot(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()),
            tutorId,
            classId);
    return snapshots.isEmpty() ? null : snapshots.get(0);
  }

  private void requestRefreshIfMissingOrStale(long tutorId, long classId) {
    Snapshot latest = latestSnapshot(tutorId, classId);
    if (latest == null || !latest.fingerprint().equals(fingerprint(tutorId, classId)))
      requestRefresh(tutorId, classId);
  }

  private void requestRefresh(long tutorId, long classId) {
    int changed =
        jdbc.update(
            "update class_insight_refresh_queue set status = 'QUEUED', requested_at = current_timestamp, last_error = null where class_id = ?",
            classId);
    if (changed == 0)
      jdbc.update(
          "insert into class_insight_refresh_queue (class_id, tutor_id, status, requested_at) values (?, ?, 'QUEUED', current_timestamp)",
          classId,
          tutorId);
  }

  private QueueStatus queueStatus(long classId) {
    List<String> status =
        jdbc.query(
            "select status from class_insight_refresh_queue where class_id = ?",
            (rs, row) -> rs.getString(1),
            classId);
    return status.isEmpty() ? QueueStatus.NONE : QueueStatus.valueOf(status.get(0));
  }

  private String fingerprint(long tutorId, long classId) {
    Settings setting = settings(tutorId, classId);
    String covered =
        coveredTopics(tutorId, classId).stream()
            .map(topic -> topic.id().toString())
            .collect(Collectors.joining(","));
    List<StudentProfile> members =
        students.findAllByTutorIdAndClassIdOrderByFullNameAsc(tutorId, classId);
    List<Long> ids = members.stream().map(StudentProfile::getId).toList();
    String scores =
        ids.isEmpty()
            ? ""
            : mastery.findAllByStudentProfileIdInWithTopic(ids).stream()
                .sorted(
                    Comparator.comparing(
                            (MasteryRecord record) -> record.getStudentProfile().getId())
                        .thenComparing(record -> record.getSyllabusTopic().getId()))
                .map(
                    record ->
                        record.getStudentProfile().getId()
                            + ":"
                            + record.getSyllabusTopic().getId()
                            + ":"
                            + record.getScore())
                .collect(Collectors.joining("|"));
    return sha256(covered + ";" + setting + ";" + ids + ";" + scores);
  }

  private void requireOwned(long tutorId, long classId) {
    if (tutorId <= 0 || classes.findByIdAndTutorId(classId, tutorId).isEmpty())
      throw new ClassService.ClassNotFoundException(classId);
  }

  private static BigDecimal percent(int part, int total) {
    return total == 0
        ? BigDecimal.ZERO
        : BigDecimal.valueOf(part)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  private static String concise(RuntimeException error) {
    return error.getMessage() == null
        ? "Refresh failed"
        : error.getMessage().substring(0, Math.min(error.getMessage().length(), 500));
  }

  private enum QueueStatus {
    NONE,
    QUEUED,
    RUNNING,
    FAILED
  }

  private record Snapshot(long id, String fingerprint, LocalDateTime dataAsOf) {}

  public record CoveredTopic(Long id, String code, String name, String nodeType) {}

  public record Settings(
      BigDecimal weakAverageMasteryPercent,
      BigDecimal weakStudentRatioPercent,
      int minimumActiveStudents) {}

  public static class InvalidInsightRequestException extends RuntimeException {
    public InvalidInsightRequestException(String message) {
      super(message);
    }
  }
}
