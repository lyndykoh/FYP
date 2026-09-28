package com.fttranscendence.learning.question;

import com.fttranscendence.learning.syllabus.SyllabusTopic;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baseline, curriculum-linked P6 Science questions. Taxonomy codes are looked up at runtime instead
 * of duplicating topic IDs, making a missing or changed syllabus fail at startup instead of quietly
 * creating unlinked content.
 */
@Component
@Order(100)
public class P6ScienceQuestionBankSeed implements ApplicationRunner {
  private final QuestionRepository questions;
  private final SyllabusTopicRepository syllabusTopics;

  public P6ScienceQuestionBankSeed(
      QuestionRepository questions, SyllabusTopicRepository syllabusTopics) {
    this.questions = questions;
    this.syllabusTopics = syllabusTopics;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    seed();
  }

  /** Safe to invoke repeatedly for migrations, local startup, and tests. */
  @Transactional
  public void seed() {
    Map<String, SyllabusTopic> topics =
        SEEDS.stream()
            .map(Seed::taxonomyCode)
            .distinct()
            .collect(Collectors.toMap(code -> code, this::requiredTopic));
    for (Seed seed : SEEDS) {
      if (questions.existsByCode(seed.code())) continue;
      Question question = new Question();
      question.setCode(seed.code());
      question.setSyllabusTopic(topics.get(seed.taxonomyCode()));
      question.setQuestionType(seed.questionType());
      question.setDifficulty(seed.difficulty());
      question.setPrompt(seed.prompt());
      question.setTotalMarks(seed.marks());
      question.setModelAnswer(seed.modelAnswer());
      question.addMarkingComponent(seed.criterion(), seed.marks(), seed.keywords());
      seed.keywords().forEach(question::addKeyword);
      questions.save(question);
    }
  }

  private SyllabusTopic requiredTopic(String code) {
    return syllabusTopics
        .findByCode(code)
        .filter(SyllabusTopic::isActive)
        .filter(topic -> topic.getNodeType() == SyllabusTopic.NodeType.SUBTOPIC)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "P6 Science question seed requires active syllabus subtopic " + code));
  }

  private record Seed(
      String code,
      String taxonomyCode,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      String prompt,
      String modelAnswer,
      String criterion,
      BigDecimal marks,
      List<String> keywords) {
    private static Seed of(
        String code,
        String taxonomyCode,
        Question.QuestionType type,
        Question.Difficulty difficulty,
        String prompt,
        String answer,
        String criterion,
        String marks,
        String... keywords) {
      return new Seed(
          code,
          taxonomyCode,
          type,
          difficulty,
          prompt,
          answer,
          criterion,
          new BigDecimal(marks),
          List.of(keywords));
    }
  }

  private static final List<Seed> SEEDS =
      List.of(
          Seed.of(
              "P6SCI-PHOTO-001",
              "SCI_P6_ENERGY_FORMS_USES_PHOTOSYNTHESIS",
              Question.QuestionType.MULTIPLE_CHOICE,
              Question.Difficulty.FOUNDATION,
              "Which combination is needed for photosynthesis? A. Sunlight, water and carbon dioxide B. Oxygen, soil and heat C. Water, oxygen and minerals D. Carbon dioxide, food and heat",
              "A. Sunlight, water and carbon dioxide.",
              "Identifies the requirements for photosynthesis",
              "1.00",
              "sunlight",
              "water",
              "carbon dioxide"),
          Seed.of(
              "P6SCI-PHOTO-002",
              "SCI_P6_ENERGY_FORMS_USES_PHOTOSYNTHESIS",
              Question.QuestionType.SHORT_ANSWER,
              Question.Difficulty.APPLICATION,
              "A plant is kept in a dark cupboard for three days. Explain why it cannot make as much food as a plant near a window.",
              "Without light energy, the plant cannot carry out photosynthesis to make food.",
              "Links light energy to photosynthesis",
              "2.00",
              "light",
              "photosynthesis",
              "food"),
          Seed.of(
              "P6SCI-PHOTO-003",
              "SCI_P6_ENERGY_FORMS_USES_PHOTOSYNTHESIS",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.CHALLENGE,
              "Design a fair test to investigate how light affects the rate of photosynthesis in pondweed. State what you change and what you measure.",
              "Change the distance or brightness of the light while keeping the plant, water and time constant; measure bubbles produced in a fixed time.",
              "Designs a controlled photosynthesis investigation",
              "3.00",
              "light",
              "control",
              "bubbles",
              "time"),
          Seed.of(
              "P6SCI-TRANSFORM-001",
              "SCI_P6_ENERGY_CONVERSION_TRANSFORMATIONS",
              Question.QuestionType.MULTIPLE_CHOICE,
              Question.Difficulty.FOUNDATION,
              "In a torch, which energy conversion happens mainly in the bulb? A. Light to chemical B. Chemical to light C. Electrical to light D. Heat to electrical",
              "C. Electrical energy is converted mainly to light energy.",
              "Identifies an energy conversion",
              "1.00",
              "electrical",
              "light"),
          Seed.of(
              "P6SCI-TRANSFORM-002",
              "SCI_P6_ENERGY_CONVERSION_TRANSFORMATIONS",
              Question.QuestionType.SHORT_ANSWER,
              Question.Difficulty.APPLICATION,
              "A solar calculator works in bright sunlight. Describe the energy changes that allow it to operate.",
              "Light energy is converted to electrical energy, which powers the calculator.",
              "Explains energy conversion in a device",
              "2.00",
              "light",
              "electrical",
              "calculator"),
          Seed.of(
              "P6SCI-TRANSFORM-003",
              "SCI_P6_ENERGY_CONVERSION_TRANSFORMATIONS",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.CHALLENGE,
              "A toy car uses a battery and a motor. Explain two useful and one unwanted energy change when the car moves.",
              "Chemical energy in the battery becomes electrical energy; the motor changes it to kinetic energy. Some energy is also changed to unwanted heat or sound.",
              "Explains useful and unwanted energy changes",
              "3.00",
              "chemical",
              "electrical",
              "kinetic",
              "heat"),
          Seed.of(
              "P6SCI-FRICTION-001",
              "SCI_P6_INTERACTIONS_FORCES_FRICTIONAL",
              Question.QuestionType.MULTIPLE_CHOICE,
              Question.Difficulty.FOUNDATION,
              "Which surface produces the greatest friction on a sliding box? A. Smooth ice B. Polished tile C. Rough carpet D. Oiled metal",
              "C. Rough carpet produces the greatest friction.",
              "Identifies a high-friction surface",
              "1.00",
              "rough",
              "friction"),
          Seed.of(
              "P6SCI-FRICTION-002",
              "SCI_P6_INTERACTIONS_FORCES_FRICTIONAL",
              Question.QuestionType.SHORT_ANSWER,
              Question.Difficulty.APPLICATION,
              "Why are the soles of running shoes patterned with grooves?",
              "The grooves increase friction with the ground and reduce slipping.",
              "Applies friction to footwear",
              "2.00",
              "friction",
              "grip",
              "slipping"),
          Seed.of(
              "P6SCI-FRICTION-003",
              "SCI_P6_INTERACTIONS_FORCES_FRICTIONAL",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.CHALLENGE,
              "A cyclist wants to travel faster on a dry road. Give one way to reduce friction and one situation where friction should be increased for safety.",
              "Lubricate moving parts or reduce air resistance to reduce friction; use good tyre tread or brakes to increase friction for safety.",
              "Balances helpful and harmful friction",
              "3.00",
              "lubricate",
              "friction",
              "tyre",
              "safety"),
          Seed.of(
              "P6SCI-GRAVITY-001",
              "SCI_P6_INTERACTIONS_FORCES_GRAVITATIONAL",
              Question.QuestionType.MULTIPLE_CHOICE,
              Question.Difficulty.FOUNDATION,
              "What force pulls a dropped ball towards Earth? A. Magnetic force B. Gravitational force C. Frictional force D. Elastic force",
              "B. Gravitational force pulls the ball towards Earth.",
              "Names gravitational force",
              "1.00",
              "gravitational",
              "earth"),
          Seed.of(
              "P6SCI-GRAVITY-002",
              "SCI_P6_INTERACTIONS_FORCES_GRAVITATIONAL",
              Question.QuestionType.SHORT_ANSWER,
              Question.Difficulty.APPLICATION,
              "A parachute opens after a skydiver jumps. Explain why the skydiver slows down even though gravity still acts.",
              "The parachute increases air resistance, which acts upward against gravity and slows the skydiver.",
              "Explains opposing forces",
              "2.00",
              "air resistance",
              "gravity",
              "slows"),
          Seed.of(
              "P6SCI-GRAVITY-003",
              "SCI_P6_INTERACTIONS_FORCES_GRAVITATIONAL",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.CHALLENGE,
              "Describe how you could use a force meter to compare the weight of two objects fairly and state what the readings show.",
              "Hang each object on the same force meter and record the force in newtons; the larger reading shows a greater gravitational pull or weight.",
              "Plans a fair comparison of weight",
              "3.00",
              "force meter",
              "newtons",
              "weight",
              "gravity"),
          Seed.of(
              "P6SCI-SPRING-001",
              "SCI_P6_INTERACTIONS_FORCES_ELASTIC_SPRING",
              Question.QuestionType.MULTIPLE_CHOICE,
              Question.Difficulty.FOUNDATION,
              "What happens to a spring when equal pulls are made in opposite directions? A. It only moves left B. It stretches C. It falls D. It becomes magnetic",
              "B. The spring stretches.",
              "Identifies elastic spring force",
              "1.00",
              "spring",
              "stretches"),
          Seed.of(
              "P6SCI-SPRING-002",
              "SCI_P6_INTERACTIONS_FORCES_ELASTIC_SPRING",
              Question.QuestionType.SHORT_ANSWER,
              Question.Difficulty.APPLICATION,
              "A spring is pulled gently and returns to its original length. What does this show about the spring?",
              "It shows the spring has an elastic force that restores its original shape or length.",
              "Explains elastic restoring force",
              "2.00",
              "elastic",
              "original",
              "shape"),
          Seed.of(
              "P6SCI-SPRING-003",
              "SCI_P6_INTERACTIONS_FORCES_ELASTIC_SPRING",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.CHALLENGE,
              "Plan an investigation to find out how the load on a spring affects its extension. Include one safety control.",
              "Add equal loads one at a time, measure extension with a ruler, keep the same spring and avoid overloading it or wear eye protection.",
              "Plans a spring-extension investigation",
              "3.00",
              "load",
              "extension",
              "measure",
              "safety"),
          Seed.of(
              "P6SCI-INTERDEPENDENCE-001",
              "SCI_P6_INTERACTIONS_ENVIRONMENT_INTERDEPENDENCE",
              Question.QuestionType.MULTIPLE_CHOICE,
              Question.Difficulty.FOUNDATION,
              "In a food chain, which organism is a producer? A. Grass B. Frog C. Hawk D. Mushroom",
              "A. Grass is a producer because it makes its own food.",
              "Identifies a producer",
              "1.00",
              "grass",
              "producer"),
          Seed.of(
              "P6SCI-INTERDEPENDENCE-002",
              "SCI_P6_INTERACTIONS_ENVIRONMENT_INTERDEPENDENCE",
              Question.QuestionType.SHORT_ANSWER,
              Question.Difficulty.APPLICATION,
              "If many caterpillars disappear from a garden food web, name one organism that may be affected and explain why.",
              "A bird that eats caterpillars may have less food, so its population may decrease or it may find another food source.",
              "Explains a food-web effect",
              "2.00",
              "food",
              "caterpillars",
              "population"),
          Seed.of(
              "P6SCI-INTERDEPENDENCE-003",
              "SCI_P6_INTERACTIONS_ENVIRONMENT_INTERDEPENDENCE",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.CHALLENGE,
              "A pond is polluted and the water plants die. Explain two likely effects on the animals in the pond ecosystem.",
              "Animals may have less food and less oxygen or shelter, so some populations may decrease and the food web may be disrupted.",
              "Predicts linked ecosystem effects",
              "3.00",
              "food",
              "oxygen",
              "population",
              "food web"));
}
