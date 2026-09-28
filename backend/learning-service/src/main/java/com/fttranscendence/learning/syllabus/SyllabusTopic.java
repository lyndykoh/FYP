package com.fttranscendence.learning.syllabus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(
    name = "syllabus_topics",
    uniqueConstraints = @UniqueConstraint(name = "uk_syllabus_topics_code", columnNames = "code"))
public class SyllabusTopic {

  public enum NodeType {
    SUBJECT(0),
    LEVEL(1),
    THEME(2),
    TOPIC(3),
    SUBTOPIC(4);

    private final int depth;

    NodeType(int depth) {
      this.depth = depth;
    }

    public int getDepth() {
      return depth;
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 120)
  @Column(nullable = false, unique = true, length = 120)
  private String code;

  @NotBlank
  @Size(max = 160)
  @Column(nullable = false, length = 160)
  private String name;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "node_type", nullable = false, length = 16)
  private NodeType nodeType;

  @Column(nullable = false)
  private short depth;

  @Column(name = "parent_id")
  private Long parentId;

  @Column(name = "parent_depth")
  private Short parentDepth;

  @Min(0)
  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @NotBlank
  @Size(max = 80)
  @Column(name = "curriculum_version", nullable = false, length = 80)
  private String curriculumVersion;

  @NotBlank
  @Size(max = 500)
  @Column(name = "source_reference", nullable = false, length = 500)
  private String sourceReference;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected SyllabusTopic() {}

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public NodeType getNodeType() {
    return nodeType;
  }

  public short getDepth() {
    return depth;
  }

  public Long getParentId() {
    return parentId;
  }

  public Short getParentDepth() {
    return parentDepth;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public String getCurriculumVersion() {
    return curriculumVersion;
  }

  public String getSourceReference() {
    return sourceReference;
  }

  public boolean isActive() {
    return active;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
