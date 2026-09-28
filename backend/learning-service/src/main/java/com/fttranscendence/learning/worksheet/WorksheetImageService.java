package com.fttranscendence.learning.worksheet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves snapshot image bytes only after worksheet ownership or assignment is proven. */
@Service
public class WorksheetImageService {
  private final WorksheetRepository worksheets;
  private final WorksheetQuestionImageRepository images;
  private final WorksheetService worksheetService;

  public WorksheetImageService(
      WorksheetRepository worksheets,
      WorksheetQuestionImageRepository images,
      WorksheetService worksheetService) {
    this.worksheets = worksheets;
    this.images = images;
    this.worksheetService = worksheetService;
  }

  @Transactional(readOnly = true)
  public ImageContent tutorImage(long tutorId, long worksheetId, long imageId) {
    worksheets
        .findByIdAndTutorId(worksheetId, tutorId)
        .orElseThrow(WorksheetService.WorksheetNotFoundException::new);
    return content(worksheetId, imageId);
  }

  @Transactional(readOnly = true)
  public ImageContent studentImage(long loginUserId, long worksheetId, long imageId) {
    worksheetService.studentAssignedWorksheet(loginUserId, worksheetId);
    return content(worksheetId, imageId);
  }

  private ImageContent content(long worksheetId, long imageId) {
    WorksheetQuestionImage image =
        images
            .findByIdAndWorksheetQuestion_Worksheet_Id(imageId, worksheetId)
            .orElseThrow(WorksheetService.WorksheetNotFoundException::new);
    return new ImageContent(image.getContentType(), image.getImageBytes());
  }

  public record ImageContent(String contentType, byte[] bytes) {}
}
