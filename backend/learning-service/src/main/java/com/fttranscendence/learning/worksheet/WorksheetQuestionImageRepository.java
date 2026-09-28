package com.fttranscendence.learning.worksheet;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface WorksheetQuestionImageRepository extends Repository<WorksheetQuestionImage, Long> {
  Optional<WorksheetQuestionImage> findByIdAndWorksheetQuestion_Worksheet_Id(
      Long imageId, Long worksheetId);
}
