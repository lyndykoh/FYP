package com.fttranscendence.learning.classroom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/** Request and response contracts for tutor-owned class management. */
public record ClassRequest(
    @NotBlank @Size(max = 120) String className,
    @NotBlank @Size(max = 80) String subject,
    @NotBlank @Size(max = 40) String level,
    @Valid @Size(max = 7) List<ScheduleRequest> schedules,
    TutorClass.Status status) {

  public ClassRequest {
    schedules =
        schedules == null
            ? List.of()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(schedules));
  }

  public record ScheduleRequest(
      @NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

  public record ScheduleResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {}

  public record ClassResponse(
      Long id,
      Long tutorId,
      String className,
      String subject,
      String level,
      TutorClass.Status status,
      List<ScheduleResponse> schedules) {
    public static ClassResponse from(TutorClass tutorClass) {
      List<ScheduleResponse> schedules =
          tutorClass.getSchedules().stream()
              .map(
                  schedule ->
                      new ScheduleResponse(
                          schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime()))
              .sorted(
                  Comparator.comparing(ScheduleResponse::dayOfWeek)
                      .thenComparing(ScheduleResponse::startTime)
                      .thenComparing(ScheduleResponse::endTime))
              .toList();
      return new ClassResponse(
          tutorClass.getId(),
          tutorClass.getTutorId(),
          tutorClass.getClassName(),
          tutorClass.getSubject(),
          tutorClass.getLevel(),
          tutorClass.getStatus(),
          schedules);
    }
  }
}
