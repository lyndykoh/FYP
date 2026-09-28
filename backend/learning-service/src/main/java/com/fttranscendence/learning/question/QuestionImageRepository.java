package com.fttranscendence.learning.question;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface QuestionImageRepository extends Repository<QuestionImage, Long> {
  <S extends QuestionImage> S save(S image);

  Optional<QuestionImage> findByIdAndQuestion_Id(Long imageId, Long questionId);

  void delete(QuestionImage image);
}
