package com.fttranscendence.learning.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record TutorNoteRequest(
    @NotBlank(message = "Note content is required")
        @Size(max = 4000, message = "Note content must not exceed 4000 characters")
        String content) {
  public record Response(
      long id, long studentId, String content, LocalDateTime createdAt, LocalDateTime updatedAt) {
    static Response from(TutorNote note) {
      return new Response(
          note.getId(),
          note.getStudentProfile().getId(),
          note.getContent(),
          note.getCreatedAt(),
          note.getUpdatedAt());
    }
  }
}
