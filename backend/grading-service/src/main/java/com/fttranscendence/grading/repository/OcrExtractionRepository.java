package com.fttranscendence.grading.repository;

import com.fttranscendence.grading.ocr.OcrExtraction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrExtractionRepository extends JpaRepository<OcrExtraction, Long> {

  List<OcrExtraction> findByPageDocumentIdOrderByPagePageNumberAsc(Long documentId);
}
