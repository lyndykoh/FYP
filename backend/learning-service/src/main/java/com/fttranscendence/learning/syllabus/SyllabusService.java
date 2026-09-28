package com.fttranscendence.learning.syllabus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only curriculum queries shared by Tutor and Student workflows. */
@Service
public class SyllabusService {

  private final SyllabusTopicRepository topics;

  public SyllabusService(SyllabusTopicRepository topics) {
    this.topics = topics;
  }

  @Transactional(readOnly = true)
  public SyllabusTreeResponse tree() {
    List<SyllabusTopic> activeTopics =
        topics.findAllByActiveTrueOrderByDepthAscSortOrderAscCodeAsc();
    Map<Long, List<SyllabusTopic>> childrenByParent = new HashMap<>();
    List<SyllabusTopic> roots = new ArrayList<>();

    for (SyllabusTopic topic : activeTopics) {
      addTopicToTree(topic, roots, childrenByParent);
    }

    return new SyllabusTreeResponse(
        roots.stream().map(topic -> node(topic, childrenByParent)).toList());
  }

  @Transactional(readOnly = true)
  public SyllabusNodeList children(Long parentId, SyllabusTopic.NodeType nodeType) {
    if (parentId != null) {
      SyllabusTopic parentTopic =
          topics
              .findById(parentId)
              .filter(SyllabusTopic::isActive)
              .orElseThrow(SyllabusNotFoundException::new);

      if (!isValidChildNodeType(parentTopic, nodeType)) {
        return new SyllabusNodeList(List.of());
      }

      List<SyllabusNode> childNodes =
          topics.findAllByParentIdAndActiveTrueOrderBySortOrderAscCodeAsc(parentId).stream()
              .filter(topic -> nodeType == null || topic.getNodeType() == nodeType)
              .map(topic -> node(topic, Map.of()))
              .toList();

      return new SyllabusNodeList(childNodes);
    }

    SyllabusTopic.NodeType rootNodeType =
        nodeType == null ? SyllabusTopic.NodeType.SUBJECT : nodeType;
    if (rootNodeType != SyllabusTopic.NodeType.SUBJECT) {
      return new SyllabusNodeList(List.of());
    }

    List<SyllabusNode> rootNodes =
        topics.findAllByNodeTypeAndActiveTrueOrderBySortOrderAscCodeAsc(rootNodeType).stream()
            .map(topic -> node(topic, Map.of()))
            .toList();

    return new SyllabusNodeList(rootNodes);
  }

  private void addTopicToTree(
      SyllabusTopic topic,
      List<SyllabusTopic> roots,
      Map<Long, List<SyllabusTopic>> childrenByParent) {
    if (topic.getParentId() == null) {
      roots.add(topic);
      return;
    }

    childrenByParent.computeIfAbsent(topic.getParentId(), ignored -> new ArrayList<>()).add(topic);
  }

  private boolean isValidChildNodeType(
      SyllabusTopic parentTopic, SyllabusTopic.NodeType requestedNodeType) {
    return requestedNodeType == null || requestedNodeType.getDepth() == parentTopic.getDepth() + 1;
  }

  private SyllabusNode node(SyllabusTopic topic, Map<Long, List<SyllabusTopic>> childrenByParent) {
    List<SyllabusTopic> orderedChildren =
        childrenByParent.getOrDefault(topic.getId(), List.of()).stream()
            .sorted(
                Comparator.comparingInt(SyllabusTopic::getSortOrder)
                    .thenComparing(SyllabusTopic::getCode))
            .toList();
    List<SyllabusNode> childNodes =
        orderedChildren.stream().map(child -> node(child, childrenByParent)).toList();

    return new SyllabusNode(
        topic.getId(),
        topic.getCode(),
        topic.getName(),
        topic.getNodeType(),
        topic.getParentId(),
        childNodes);
  }

  public record SyllabusTreeResponse(List<SyllabusNode> items) {}

  public record SyllabusNodeList(List<SyllabusNode> items) {}

  public record SyllabusNode(
      long id,
      String code,
      String name,
      SyllabusTopic.NodeType nodeType,
      Long parentId,
      List<SyllabusNode> children) {}

  public static final class SyllabusNotFoundException extends RuntimeException {}
}
