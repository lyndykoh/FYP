import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { TutorWorksheet } from "@/services/worksheets";
import TutorWorksheetDetail from "./TutorWorksheetDetail";

const draft = {
  id: 1,
  code: "WS-1",
  title: "Water drill",
  instructions: "Answer in complete sentences.",
  subject: "Science",
  worksheetType: "DIAGNOSTIC" as const,
  targetMode: "CLASS" as const,
  status: "DRAFT" as const,
  generationRequestId: 8,
  dueAt: null,
  assignments: [],
  questions: [
    {
      id: 2,
      code: "Q",
      prompt: "Explain evaporation.",
      totalMarks: 2,
      questionType: "OPEN_ENDED" as const,
      topicId: 5,
      topicName: "Water",
    },
  ],
};

describe("TutorWorksheetDetail", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("renders real worksheet provenance, question metadata, and Tutor-only draft actions", () => {
    render(<TutorWorksheetDetail worksheet={draft} />);
    expect(screen.getByText("GENERATED")).toBeVisible();
    expect(screen.getByText("Science · 1 question · 2.0 marks")).toBeVisible();
    expect(
      screen.getByText("Q · Water · Open Ended · 2.0 marks"),
    ).toBeVisible();
    expect(screen.getByLabelText("Generation provenance")).toHaveTextContent(
      "Generated from request #8",
    );
    expect(
      screen.getByRole("button", { name: "Approve & assign worksheet" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "Upload student work" }),
    ).not.toBeInTheDocument();
  });

  it("keeps Tutor management distinct and approves a draft", async () => {
    const approve = vi
      .fn()
      .mockResolvedValue({
        ...draft,
        status: "APPROVED",
        assignments: [
          {
            id: 4,
            assignmentType: "CLASS",
            classId: 3,
            studentProfileId: null,
            assignedAt: "2026-08-27T10:00:00",
            dueAt: null,
          },
        ],
      });
    const onApproved = vi.fn();
    render(
      <TutorWorksheetDetail
        worksheet={draft}
        approve={approve}
        onApproved={onApproved}
      />,
    );
    await userEvent
      .setup()
      .click(
        screen.getByRole("button", { name: "Approve & assign worksheet" }),
      );
    expect(approve).toHaveBeenCalledWith(1, undefined);
    expect(await screen.findByText("ASSIGNED")).toBeVisible();
    expect(onApproved).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1, status: "APPROVED" }),
    );
    expect(
      screen.getByRole("link", { name: "Upload student work" }),
    ).toHaveAttribute("href", "/upload?worksheetId=1");
    expect(
      screen.getByRole("link", { name: "Enter result manually" }),
    ).toHaveAttribute("href", "/tutor/worksheets/1/results/new");
  });

  it("shows an approval error instead of the sent confirmation when assignment fails", async () => {
    const approve = vi
      .fn()
      .mockRejectedValue(new Error("Assignment service is unavailable."));
    const onApproved = vi.fn();
    render(
      <TutorWorksheetDetail
        worksheet={draft}
        approve={approve}
        onApproved={onApproved}
      />,
    );

    await userEvent
      .setup()
      .click(
        screen.getByRole("button", { name: "Approve & assign worksheet" }),
      );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Assignment service is unavailable.",
    );
    expect(onApproved).not.toHaveBeenCalled();
  });

  it("submits detail approval once while the request is pending", async () => {
    let resolveApproval: (worksheet: TutorWorksheet) => void;
    const approvalPending = new Promise<TutorWorksheet>((resolve) => {
      resolveApproval = resolve;
    });
    const approve = vi.fn().mockReturnValue(approvalPending);
    const onApproved = vi.fn();
    render(
      <TutorWorksheetDetail
        worksheet={draft}
        approve={approve}
        onApproved={onApproved}
      />,
    );

    const approveButton = screen.getByRole("button", {
      name: "Approve & assign worksheet",
    });
    fireEvent.click(approveButton);
    fireEvent.click(approveButton);
    expect(approve).toHaveBeenCalledTimes(1);
    expect(onApproved).not.toHaveBeenCalled();

    await act(async () => {
      resolveApproval!({
        ...draft,
        status: "APPROVED",
        assignments: [
          {
            id: 4,
            assignmentType: "CLASS",
            classId: 3,
            studentProfileId: null,
            assignedAt: "2026-08-27T10:00:00",
            dueAt: null,
          },
        ],
      });
    });
    expect(onApproved).toHaveBeenCalledTimes(1);
  });

  it("edits a draft and wires approved PDF export", async () => {
    const update = vi.fn().mockResolvedValue(draft);
    const downloadPdf = vi
      .fn()
      .mockResolvedValue(new Blob(["pdf"], { type: "application/pdf" }));
    const user = userEvent.setup();
    render(<TutorWorksheetDetail worksheet={draft} update={update} />);
    await user.click(screen.getByRole("button", { name: "Edit worksheet" }));
    const titleInput = screen.getByRole("textbox", { name: /title/i });
    await user.clear(titleInput);
    await user.type(titleInput, "Updated water drill");
    await user.click(
      screen.getByRole("button", { name: "Save worksheet draft" }),
    );
    expect(update).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        title: "Updated water drill",
        questionIds: [2],
      }),
    );

    const approved = {
      ...draft,
      status: "APPROVED" as const,
      assignments: [
        {
          id: 4,
          assignmentType: "CLASS" as const,
          classId: 3,
          studentProfileId: null,
          assignedAt: null,
          dueAt: null,
        },
      ],
    };
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn().mockReturnValue("blob:worksheet"),
      revokeObjectURL: vi.fn(),
    });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => undefined);
    render(
      <TutorWorksheetDetail worksheet={approved} downloadPdf={downloadPdf} />,
    );
    await user.click(
      screen.getAllByRole("button", { name: "Download PDF" }).at(-1)!,
    );
    expect(downloadPdf).toHaveBeenCalledWith(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:worksheet");
    click.mockRestore();
  });

  it("lets a Tutor remove, replace, and add active question-bank items before saving a draft", async () => {
    const secondQuestion = {
      id: 7,
      code: "Q-7",
      prompt: "Describe condensation.",
      totalMarks: 3,
      questionType: "OPEN_ENDED" as const,
      topicId: 5,
      topicName: "Water",
    };
    const replacement = {
      id: 8,
      code: "Q-8",
      prompt: "Name a stage of the water cycle.",
      totalMarks: 1,
      questionType: "SHORT_ANSWER" as const,
      syllabusTopic: {
        id: 5,
        code: "SCI-WATER",
        name: "Water",
        nodeType: "TOPIC" as const,
      },
      archiveState: "ACTIVE" as const,
    };
    const addition = {
      id: 9,
      code: "Q-9",
      prompt: "Why does rain fall?",
      totalMarks: 2,
      questionType: "OPEN_ENDED" as const,
      syllabusTopic: {
        id: 5,
        code: "SCI-WATER",
        name: "Water",
        nodeType: "TOPIC" as const,
      },
      archiveState: "ACTIVE" as const,
    };
    const twoQuestionDraft = {
      ...draft,
      questions: [draft.questions[0], secondQuestion],
    };
    const update = vi.fn().mockResolvedValue({
      ...twoQuestionDraft,
      questions: [
        { ...replacement, topicId: 5, topicName: "Water" },
        { ...addition, topicId: 5, topicName: "Water" },
      ],
    });
    const loadQuestions = vi
      .fn()
      .mockResolvedValue({
        items: [replacement, addition],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
        hasNext: false,
      });
    const user = userEvent.setup();
    render(
      <TutorWorksheetDetail
        worksheet={twoQuestionDraft}
        update={update}
        loadQuestions={loadQuestions}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Edit worksheet" }));
    await user.click(
      screen.getByRole("button", { name: "Load active question bank" }),
    );
    expect(loadQuestions).toHaveBeenCalledWith({
      topicId: 5,
      archiveState: "ACTIVE",
      size: 100,
    });

    await user.click(
      screen.getByRole("button", { name: "Replace question 1" }),
    );
    await user.click(screen.getByRole("button", { name: "Replace with Q-8" }));
    await user.click(screen.getByRole("button", { name: "Add Q-9" }));
    await user.click(screen.getByRole("button", { name: "Remove question 2" }));
    await user.click(
      screen.getByRole("button", { name: "Save worksheet draft" }),
    );

    expect(update).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ questionIds: [8, 9] }),
    );
  });

  it("renders legacy snapshot fallback and leaves archived worksheets read-only", () => {
    render(
      <TutorWorksheetDetail
        worksheet={{
          ...draft,
          subject: null,
          worksheetType: undefined,
          generationRequestId: null,
          status: "ARCHIVED",
        }}
      />,
    );
    expect(
      screen.getByText(/Subject not recorded/, { selector: "p" }),
    ).toBeVisible();
    expect(screen.getByLabelText("Generation provenance")).toHaveTextContent(
      "legacy worksheet has no generation request",
    );
    expect(screen.getByRole("status")).toHaveTextContent("read-only record");
    expect(
      screen.queryByRole("button", { name: "Download PDF" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Approve & assign worksheet" }),
    ).not.toBeInTheDocument();
  });

  it("keeps the work area and support rail fluid at a mobile viewport", () => {
    const previousWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: 320,
    });
    render(<TutorWorksheetDetail worksheet={draft} />);
    expect(screen.getByTestId("tutor-worksheet-detail")).toBeVisible();
    const styles = [...document.querySelectorAll("style")]
      .map((style) => style.textContent ?? "")
      .join("\n");
    expect(styles).toMatch(/flex:1 1 460px/);
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: previousWidth,
    });
  });
});
