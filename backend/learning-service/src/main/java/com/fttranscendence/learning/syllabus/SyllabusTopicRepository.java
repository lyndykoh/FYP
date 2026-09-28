package com.fttranscendence.learning.syllabus;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface SyllabusTopicRepository extends Repository<SyllabusTopic, Long> {

  Optional<SyllabusTopic> findById(Long id);

  Optional<SyllabusTopic> findByCode(String code);

  List<SyllabusTopic> findAllByParentIdAndActiveTrueOrderBySortOrderAscCodeAsc(Long parentId);

  List<SyllabusTopic> findAllByNodeTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
      SyllabusTopic.NodeType nodeType);

  List<SyllabusTopic> findAllByActiveTrueOrderByDepthAscSortOrderAscCodeAsc();

  List<SyllabusTopic> findAllById(Iterable<Long> ids);

  long countByCurriculumVersionAndActiveTrue(String curriculumVersion);
}
