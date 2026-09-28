package com.fttranscendence.learning.classroom;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
    name = "tutor_classes",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_tutor_classes_owner_name",
            columnNames = {"tutor_id", "normalized_class_name"}))
public class TutorClass {

  public enum Status {
    ACTIVE,
    INACTIVE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Positive
  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @NotBlank
  @Size(max = 120)
  @Column(name = "class_name", nullable = false, length = 120)
  private String className;

  @Column(name = "normalized_class_name", nullable = false, length = 120)
  private String normalizedClassName;

  @NotBlank
  @Size(max = 80)
  @Column(nullable = false, length = 80)
  private String subject;

  @NotBlank
  @Size(max = 40)
  @Column(name = "class_level", nullable = false, length = 40)
  private String level;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.ACTIVE;

  @Valid
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "class_schedules",
      joinColumns = @JoinColumn(name = "class_id", nullable = false))
  private Set<ScheduleSlot> schedules = new LinkedHashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prepareForInsert() {
    normalizeFields();
    if (status == null) {
      status = Status.ACTIVE;
    }
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void prepareForUpdate() {
    normalizeFields();
    updatedAt = LocalDateTime.now();
  }

  private void normalizeFields() {
    if (className != null) {
      className = className.trim();
      normalizedClassName = className.toLowerCase(Locale.ROOT);
    }
    if (subject != null) {
      subject = subject.trim();
    }
    if (level != null) {
      level = level.trim();
    }
  }

  public Long getId() {
    return id;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public void setTutorId(Long tutorId) {
    this.tutorId = tutorId;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Set<ScheduleSlot> getSchedules() {
    return schedules;
  }

  public void setSchedules(Set<ScheduleSlot> schedules) {
    this.schedules = schedules == null ? new LinkedHashSet<>() : new LinkedHashSet<>(schedules);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  @Embeddable
  public static class ScheduleSlot {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected ScheduleSlot() {}

    public ScheduleSlot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
      this.dayOfWeek = dayOfWeek;
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public DayOfWeek getDayOfWeek() {
      return dayOfWeek;
    }

    public LocalTime getStartTime() {
      return startTime;
    }

    public LocalTime getEndTime() {
      return endTime;
    }

    @Override
    public boolean equals(Object candidate) {
      if (this == candidate) {
        return true;
      }
      if (!(candidate instanceof ScheduleSlot other)) {
        return false;
      }
      return dayOfWeek == other.dayOfWeek
          && Objects.equals(startTime, other.startTime)
          && Objects.equals(endTime, other.endTime);
    }

    @Override
    public int hashCode() {
      return Objects.hash(dayOfWeek, startTime, endTime);
    }
  }
}
