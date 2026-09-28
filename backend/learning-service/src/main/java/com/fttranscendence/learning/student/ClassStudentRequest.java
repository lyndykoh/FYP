package com.fttranscendence.learning.student;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Identifies an existing auth-service Student account to add to a class. */
public record ClassStudentRequest(@NotNull @Positive Long loginUserId) {}
