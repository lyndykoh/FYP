package com.fttranscendence.learning.worksheet;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface WorksheetGenerationRequestRepository
    extends Repository<WorksheetGenerationRequest, Long> {
  <S extends WorksheetGenerationRequest> S save(S request);

  Optional<WorksheetGenerationRequest> findByIdAndTutorId(Long id, Long tutorId);

  Optional<WorksheetGenerationRequest> findByTutorIdAndIdempotencyKey(
      Long tutorId, String idempotencyKey);
}
