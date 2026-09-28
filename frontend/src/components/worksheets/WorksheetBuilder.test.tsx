import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { TutorClass } from "@/services/classes";
import type { SyllabusTree } from "@/services/syllabus";
import type { TutorStudent } from "@/services/students";
import { WorksheetBuilder } from "./WorksheetBuilder";

const syllabus: SyllabusTree = {
  items: [
    {
      id: 1,
      code: "SCI",
      name: "Science",
      nodeType: "SUBJECT",
      parentId: null,
      children: [
        {
          id: 2,
          code: "SCI_P5",
          name: "Primary 5",
          nodeType: "LEVEL",
          parentId: 1,
          children: [
            {
              id: 3,
              code: "SCI_P5_CYCLES",
              name: "Cycles",
              nodeType: "THEME",
              parentId: 2,
              children: [
                {
                  id: 4,
                  code: "SCI_WATER",
                  name: "Water",
                  nodeType: "TOPIC",
                  parentId: 3,
                  children: [],
                },
              ],
            },
          ],
        },
      ],
    },
  ],
};
const classes: TutorClass[] = [
  {
    id: 1,
    tutorId: 7,
    className: "P5 Curie",
    subject: "Science",
    level: "Primary 5",
    status: "ACTIVE",
    schedules: [],
  },
  {
    id: 2,
    tutorId: 7,
    className: "P4 Faraday",
    subject: "Science",
    level: "Primary 4",
    status: "ACTIVE",
    schedules: [],
  },
];
const students: TutorStudent[] = [
  {
    id: 8,
    tutorId: 7,
    fullName: "Ari Tan",
    loginUserId: 108,
    classes: [
      { id: 1, className: "P5 Curie", subject: "Science", level: "Primary 5" },
    ],
    createdAt: "2026-01-01",
    updatedAt: "2026-01-01",
  },
];
const questions = async () => ({
  items: [],
  page: 0,
  size: 100,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
});

async function choose(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string | RegExp,
) {
  await user.click(await screen.findByLabelText(label));
  await user.click(await screen.findByRole("option", { name: option }));
}

async function chooseClass(
  user: ReturnType<typeof userEvent.setup>,
  name: string | RegExp = /P5 Curie/,
) {
  await choose(user, "Class", name);
}

async function continueToConfiguration(
  user: ReturnType<typeof userEvent.setup>,
) {
  await user.click(
    screen.getByRole("button", { name: "Continue to configuration" }),
  );
  await screen.findByRole("heading", { name: "Configure worksheet" });
}

async function configureWaterTopic(user: ReturnType<typeof userEvent.setup>) {
  await choose(user, "Theme", "Cycles");
  await choose(user, "Topic", "Water");
}

describe("WorksheetBuilder", () => {
  it("defaults all worksheet Question Bank filters to Any and generates without topic IDs", async () => {
    const user = userEvent.setup();
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: {
          id: 9,
          code: "GEN-9",
          title: "Mixed practice",
          instructions: null,
          targetMode: "CLASS",
          status: "DRAFT",
          dueAt: null,
          questions: [
            {
              id: 2,
              code: "Q",
              prompt: "Explain evaporation.",
              totalMarks: 2,
              questionType: "OPEN_ENDED",
              topicName: "Water",
            },
          ],
          assignments: [],
        },
      });
    render(
      <WorksheetBuilder
        generate={generate}
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadQuestions={questions}
      />,
    );

    await chooseClass(user);
    await continueToConfiguration(user);
    expect(screen.getByLabelText("Theme")).toHaveTextContent("Any Theme");
    expect(screen.getByLabelText("Topic")).toHaveTextContent("Any Topic");
    expect(screen.getByLabelText("Question type")).toHaveTextContent(
      "Any Question Type",
    );
    expect(screen.getByLabelText("Difficulty")).toHaveTextContent(
      "Any Difficulty",
    );

    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    expect(generate).toHaveBeenCalledWith(
      1,
      expect.not.objectContaining({
        topicIds: expect.anything(),
        questionType: expect.anything(),
        difficulty: expect.anything(),
      }),
      expect.any(String),
    );
  });

  it("keeps Any question type and difficulty unrestricted for a specific topic", async () => {
    const user = userEvent.setup();
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: {
          id: 9,
          code: "GEN-9",
          title: "Water practice",
          instructions: null,
          targetMode: "CLASS",
          status: "DRAFT",
          dueAt: null,
          questions: [
            {
              id: 2,
              code: "Q",
              prompt: "Explain evaporation.",
              totalMarks: 2,
              questionType: "OPEN_ENDED",
              topicName: "Water",
            },
          ],
          assignments: [],
        },
      });
    render(
      <WorksheetBuilder
        generate={generate}
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadQuestions={questions}
      />,
    );

    await chooseClass(user);
    await continueToConfiguration(user);
    await configureWaterTopic(user);
    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    expect(generate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ topicIds: [4] }),
      expect.any(String),
    );
    expect(generate.mock.calls[0]?.[1]).not.toHaveProperty("questionType");
    expect(generate.mock.calls[0]?.[1]).not.toHaveProperty("difficulty");
  });

  it("keeps Any Topic unrestricted when filtering by question type", async () => {
    const user = userEvent.setup();
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: {
          id: 9,
          code: "GEN-9",
          title: "Open-ended practice",
          instructions: null,
          targetMode: "CLASS",
          status: "DRAFT",
          dueAt: null,
          questions: [
            {
              id: 2,
              code: "Q",
              prompt: "Explain evaporation.",
              totalMarks: 2,
              questionType: "OPEN_ENDED",
              topicName: "Water",
            },
          ],
          assignments: [],
        },
      });
    render(
      <WorksheetBuilder
        generate={generate}
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadQuestions={questions}
      />,
    );

    await chooseClass(user);
    await continueToConfiguration(user);
    await choose(user, "Question type", "Open ended");
    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    expect(generate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ questionType: "OPEN_ENDED" }),
      expect.any(String),
    );
    expect(generate.mock.calls[0]?.[1]).not.toHaveProperty("topicIds");
    expect(generate.mock.calls[0]?.[1]).not.toHaveProperty("difficulty");
  });

  it("loads Tutor classes in the generator, selects a class, then generates and approves a draft", async () => {
    const user = userEvent.setup();
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: {
          id: 9,
          code: "GEN-9",
          title: "Water drill",
          instructions: null,
          targetMode: "CLASS",
          status: "DRAFT",
          dueAt: null,
          questions: [
            {
              id: 2,
              code: "Q",
              prompt: "Explain evaporation.",
              totalMarks: 2,
              questionType: "OPEN_ENDED",
              topicName: "Water",
            },
          ],
          assignments: [],
        },
      });
    const approve = vi
      .fn()
      .mockResolvedValue({
        id: 9,
        code: "GEN-9",
        title: "Water drill",
        instructions: null,
        targetMode: "CLASS",
        status: "APPROVED",
        dueAt: null,
        questions: [],
        assignments: [],
      });
    const loadClasses = vi.fn().mockResolvedValue(classes);
    const onApproved = vi.fn();
    render(
      <WorksheetBuilder
        generate={generate}
        approve={approve}
        loadClasses={loadClasses}
        loadSyllabus={async () => syllabus}
        loadQuestions={questions}
        onApproved={onApproved}
      />,
    );

    await chooseClass(user);
    await continueToConfiguration(user);
    expect(screen.getByText(/Whole class.*P5 Curie/i)).toBeVisible();
    await configureWaterTopic(user);
    await choose(user, "Question type", "Open ended");
    await choose(user, "Difficulty", "Challenge");
    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    expect(
      await screen.findByText("Explain evaporation.", { exact: false }),
    ).toBeVisible();
    expect(loadClasses).toHaveBeenCalledTimes(1);
    expect(generate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        topicIds: [4],
        questionType: "OPEN_ENDED",
        difficulty: "CHALLENGE",
      }),
      expect.any(String),
    );
    await user.click(
      screen.getByRole("button", { name: "Approve & assign worksheet" }),
    );
    expect(approve).toHaveBeenCalledWith(9, undefined);
    expect(onApproved).toHaveBeenCalledWith(
      expect.objectContaining({ id: 9, status: "APPROVED" }),
    );
  });

  it("shows an approval error instead of the sent confirmation when the assignment fails", async () => {
    const user = userEvent.setup();
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: {
          id: 9,
          code: "GEN-9",
          title: "Water drill",
          instructions: null,
          targetMode: "CLASS",
          status: "DRAFT",
          dueAt: null,
          questions: [
            {
              id: 2,
              code: "Q",
              prompt: "Explain evaporation.",
              totalMarks: 2,
              questionType: "OPEN_ENDED",
              topicName: "Water",
            },
          ],
          assignments: [],
        },
      });
    const approve = vi
      .fn()
      .mockRejectedValue(new Error("Assignment service is unavailable."));
    const onApproved = vi.fn();
    render(
      <WorksheetBuilder
        generate={generate}
        approve={approve}
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadQuestions={questions}
        onApproved={onApproved}
      />,
    );

    await chooseClass(user);
    await continueToConfiguration(user);
    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Approve & assign worksheet" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Assignment service is unavailable.",
    );
    expect(onApproved).not.toHaveBeenCalled();
  });

  it("submits builder approval once while the request is pending", async () => {
    const user = userEvent.setup();
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: {
          id: 9,
          code: "GEN-9",
          title: "Water drill",
          instructions: null,
          targetMode: "CLASS",
          status: "DRAFT",
          dueAt: null,
          questions: [
            {
              id: 2,
              code: "Q",
              prompt: "Explain evaporation.",
              totalMarks: 2,
              questionType: "OPEN_ENDED",
              topicName: "Water",
            },
          ],
          assignments: [],
        },
      });
    let resolveApproval: (worksheet: {
      id: number;
      code: string;
      title: string;
      instructions: null;
      targetMode: "CLASS";
      status: "APPROVED";
      dueAt: null;
      questions: [];
      assignments: [];
    }) => void;
    const approvalPending = new Promise<{
      id: number;
      code: string;
      title: string;
      instructions: null;
      targetMode: "CLASS";
      status: "APPROVED";
      dueAt: null;
      questions: [];
      assignments: [];
    }>((resolve) => {
      resolveApproval = resolve;
    });
    const approve = vi.fn().mockReturnValue(approvalPending);
    const onApproved = vi.fn();
    render(
      <WorksheetBuilder
        generate={generate}
        approve={approve}
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadQuestions={questions}
        onApproved={onApproved}
      />,
    );

    await chooseClass(user);
    await continueToConfiguration(user);
    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    const approveButton = await screen.findByRole("button", {
      name: "Approve & assign worksheet",
    });
    fireEvent.click(approveButton);
    fireEvent.click(approveButton);
    expect(approve).toHaveBeenCalledTimes(1);
    expect(onApproved).not.toHaveBeenCalled();

    await act(async () => {
      resolveApproval!({
        id: 9,
        code: "GEN-9",
        title: "Water drill",
        instructions: null,
        targetMode: "CLASS",
        status: "APPROVED",
        dueAt: null,
        questions: [],
        assignments: [],
      });
    });
    expect(onApproved).toHaveBeenCalledTimes(1);
  });

  it("loads class-filtered students, requires a student selection, and presents diagnostics as a suggestion", async () => {
    const user = userEvent.setup();
    const diagnostic = vi
      .fn()
      .mockResolvedValue({
        status: "READY",
        message: "Evidence is ready.",
        recommendations: [
          {
            studentId: 8,
            studentName: "Ari Tan",
            topicId: 4,
            topicName: "Water",
            masteryPercent: 42,
            attemptCount: 2,
            reason: "LOW_MASTERY",
          },
        ],
      });
    const loadStudents = vi.fn().mockResolvedValue(students);
    render(
      <WorksheetBuilder
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadStudents={loadStudents}
        loadDiagnostic={diagnostic}
      />,
    );

    await user.click(screen.getByRole("button", { name: /Selected students/ }));
    await chooseClass(user);
    expect(await screen.findByLabelText("Ari Tan")).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Continue to configuration" }),
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Choose at least one student target",
    );
    await user.click(screen.getByLabelText("Ari Tan"));
    await continueToConfiguration(user);
    await user.click(
      screen.getByRole("button", { name: "Get diagnostic suggestions" }),
    );
    expect(await screen.findByText(/Diagnostic recommendation/i)).toBeVisible();
    expect(
      screen.getByText(/Suggestion only — not saved or assigned/i),
    ).toBeVisible();
    expect(loadStudents).toHaveBeenCalledWith(1);
    expect(diagnostic).toHaveBeenCalledWith(1);
  });

  it("preselects valid class and student query context and retains it through configuration", async () => {
    const user = userEvent.setup();
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: {
          id: 9,
          code: "GEN-9",
          title: "Water drill",
          instructions: null,
          targetMode: "STUDENTS",
          status: "DRAFT",
          dueAt: null,
          questions: [
            {
              id: 2,
              code: "Q",
              prompt: "Explain evaporation.",
              totalMarks: 2,
              questionType: "OPEN_ENDED",
              topicName: "Water",
            },
          ],
          assignments: [],
        },
      });
    render(
      <WorksheetBuilder
        classId={1}
        initialStudentId={8}
        generate={generate}
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadStudents={async () => students}
        loadQuestions={questions}
      />,
    );

    expect(
      await screen.findByRole("checkbox", { name: "Ari Tan" }),
    ).toBeChecked();
    await continueToConfiguration(user);
    expect(screen.getByText(/1 selected student.*P5 Curie/i)).toBeVisible();
    await configureWaterTopic(user);
    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    expect(generate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ targetMode: "STUDENTS", studentIds: [8] }),
      expect.any(String),
    );
  });

  it("retains future student-only context until the Tutor selects that student's class", async () => {
    const user = userEvent.setup();
    const loadStudents = vi.fn().mockResolvedValue(students);
    render(
      <WorksheetBuilder
        initialStudentId={8}
        loadClasses={async () => classes}
        loadStudents={loadStudents}
      />,
    );

    await chooseClass(user);
    expect(
      await screen.findByRole("checkbox", { name: "Ari Tan" }),
    ).toBeChecked();
    expect(loadStudents).toHaveBeenCalledWith(1);
    await continueToConfiguration(user);
    expect(screen.getByText(/1 selected student.*P5 Curie/i)).toBeVisible();
  });

  it("reports invalid preselected class context and clears it when the Tutor chooses a valid replacement", async () => {
    const user = userEvent.setup();
    render(<WorksheetBuilder classId={99} loadClasses={async () => classes} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "requested class is unavailable",
    );
    await chooseClass(user);
    expect(
      screen.queryByText(/requested class is unavailable/i),
    ).not.toBeInTheDocument();
  });

  it("renders loading, empty, and recoverable class loading failure states", async () => {
    let resolveClasses: ((value: TutorClass[]) => void) | undefined;
    const pendingClasses = new Promise<TutorClass[]>((resolve) => {
      resolveClasses = resolve;
    });
    const { rerender } = render(
      <WorksheetBuilder loadClasses={() => pendingClasses} />,
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "Loading your classes",
    );
    resolveClasses?.([]);
    expect(
      await screen.findByText(/do not have any classes yet/i),
    ).toBeVisible();
    rerender(
      <WorksheetBuilder
        loadClasses={async () => {
          throw new Error("Class service is offline");
        }}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Class service is offline",
    );
  });

  it("retries a failed class request and blocks continuation until a valid class is selected", async () => {
    const user = userEvent.setup();
    const loadClasses = vi
      .fn()
      .mockRejectedValueOnce(new Error("Class service is offline"))
      .mockResolvedValueOnce(classes);
    render(<WorksheetBuilder loadClasses={loadClasses} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Class service is offline",
    );
    await user.click(screen.getByRole("button", { name: "Retry classes" }));
    await screen.findByLabelText("Class");
    await user.click(
      screen.getByRole("button", { name: "Continue to configuration" }),
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Choose a valid class before continuing",
    );
  });

  it("renders a student API failure and lets the tutor retry after a class is selected", async () => {
    const user = userEvent.setup();
    const loadStudents = vi
      .fn()
      .mockRejectedValueOnce(new Error("Student service is offline"))
      .mockResolvedValueOnce(students);
    render(
      <WorksheetBuilder
        loadClasses={async () => classes}
        loadStudents={loadStudents}
      />,
    );
    await user.click(screen.getByRole("button", { name: /Selected students/ }));
    await chooseClass(user);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Student service is offline",
    );
    await user.click(screen.getByRole("button", { name: "Retry students" }));
    expect(await screen.findByLabelText("Ari Tan")).toBeInTheDocument();
  });

  it("saves keyboard-accessible reorder changes through the draft update API", async () => {
    const user = userEvent.setup();
    const initial = {
      id: 11,
      code: "GEN-11",
      title: "Water drill",
      instructions: null,
      targetMode: "CLASS" as const,
      status: "DRAFT" as const,
      dueAt: null,
      questions: [
        {
          id: 2,
          code: "Q-2",
          prompt: "Explain evaporation.",
          totalMarks: 2,
          questionType: "OPEN_ENDED" as const,
          topicId: 4,
          topicName: "Water",
        },
        {
          id: 3,
          code: "Q-3",
          prompt: "Explain condensation.",
          totalMarks: 2,
          questionType: "OPEN_ENDED" as const,
          topicId: 4,
          topicName: "Water",
        },
      ],
      assignments: [],
    };
    const generate = vi
      .fn()
      .mockResolvedValue({
        id: 1,
        status: "SUCCEEDED",
        message: "Ready",
        worksheet: initial,
      });
    const update = vi.fn().mockResolvedValue(initial);
    render(
      <WorksheetBuilder
        classId={1}
        generate={generate}
        update={update}
        loadClasses={async () => classes}
        loadSyllabus={async () => syllabus}
        loadQuestions={async () => ({
          items: [
            {
              id: 4,
              code: "Q-4",
              syllabusTopic: {
                id: 4,
                code: "SCI-WATER",
                name: "Water",
                nodeType: "SUBTOPIC" as const,
              },
              questionType: "OPEN_ENDED" as const,
              prompt: "Explain boiling.",
              totalMarks: 2,
              archiveState: "ACTIVE" as const,
            },
          ],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        })}
      />,
    );
    await continueToConfiguration(user);
    await configureWaterTopic(user);
    await user.click(
      screen.getByRole("button", { name: "Generate worksheet draft" }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Move question 2 up" }),
    );
    expect(update).toHaveBeenCalledWith(
      11,
      expect.objectContaining({ questionIds: [3, 2] }),
    );
  });
});
