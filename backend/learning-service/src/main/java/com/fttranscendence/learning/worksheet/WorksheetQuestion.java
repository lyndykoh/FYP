package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.question.Question;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "worksheet_questions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_worksheet_questions_question",
          columnNames = {"worksheet_id", "question_id"}),
      @UniqueConstraint(
          name = "uk_worksheet_questions_position",
          columnNames = {"worksheet_id", "position"})
    })
public class WorksheetQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "worksheet_id", nullable = false)
  private Worksheet worksheet;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Min(0)
  @Column(nullable = false)
  private int position;

  /** Immutable question-bank values captured when the question is added. */
  @Size(max = 120)
  @Column(name = "question_code_snapshot", length = 120)
  private String questionCodeSnapshot;

  @Size(max = 4000)
  @Column(name = "prompt_snapshot", length = 4000)
  private String promptSnapshot;

  @Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "question_type_snapshot", length = 32)
  private Question.QuestionType questionTypeSnapshot;

  @DecimalMin(value = "0.01")
  private BigDecimal totalMarksSnapshot;

  @OneToMany(
      mappedBy = "worksheetQuestion",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  private List<WorksheetQuestionImage> images = new ArrayList<>();

  protected WorksheetQuestion() {}

  WorksheetQuestion(Worksheet worksheet, Question question, int position) {
    this.worksheet = worksheet;
    this.question = question;
    this.position = position;
    snapshot(question);
  }

  private void snapshot(Question source) {
    questionCodeSnapshot = source.getCode();
    promptSnapshot = source.getPrompt();
    questionTypeSnapshot = source.getQuestionType();
    totalMarksSnapshot = source.getTotalMarks();
    source
        .getImages()
        .forEach(image -> images.add(new WorksheetQuestionImage(this, images.size(), image)));
  }

  void attachTo(Worksheet worksheet) {
    this.worksheet = worksheet;
  }

  void detach() {
    worksheet = null;
  }

  void setPosition(int position) {
    this.position = position;
  }

  public Long getId() {
    return id;
  }

  public Question getQuestion() {
    return question;
  }

  public int getPosition() {
    return position;
  }

  public String getQuestionCodeSnapshot() {
    return questionCodeSnapshot;
  }

  public String getPromptSnapshot() {
    return promptSnapshot;
  }

  public Question.QuestionType getQuestionTypeSnapshot() {
    return questionTypeSnapshot;
  }

  public BigDecimal getTotalMarksSnapshot() {
    return totalMarksSnapshot;
  }

  public List<WorksheetQuestionImage> getImages() {
    return List.copyOf(images);
  }
}
