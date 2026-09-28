import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type {
  QuestionBankFilters,
  QuestionBankPage,
} from "@/services/questions";
import type { SyllabusTree } from "@/services/syllabus";
import QuestionList from "./QuestionList";

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
                  id: 14,
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

async function choose(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) {
  await user.click(await screen.findByLabelText(label));
  await user.click(await screen.findByRole("option", { name: option }));
}

const firstPage: QuestionBankPage = {
  items: [
    {
      id: 1,
      code: "SCI-WATER-001",
      syllabusTopic: {
        id: 14,
        code: "SCI-WATER",
        name: "Water",
        nodeType: "SUBTOPIC",
      },
      questionType: "OPEN_ENDED",
      prompt: "Explain evaporation.",
      totalMarks: 2,
      archiveState: "ACTIVE",
    },
  ],
  page: 0,
  size: 12,
  totalElements: 2,
  totalPages: 2,
  hasNext: true,
};
const secondPage: QuestionBankPage = {
  items: [
    {
      id: 2,
      code: "SCI-ENERGY-001",
      syllabusTopic: {
        id: 16,
        code: "SCI-ENERGY",
        name: "Energy conversion",
        nodeType: "TOPIC",
      },
      questionType: "MULTIPLE_CHOICE",
      prompt: "Choose the energy conversion.",
      totalMarks: 1,
      archiveState: "ACTIVE",
    },
  ],
  page: 1,
  size: 12,
  totalElements: 2,
  totalPages: 2,
  hasNext: false,
};

describe("QuestionList", () => {
  it("shows loading before rendering question cards", async () => {
    let resolve!: (page: QuestionBankPage) => void;
    render(
      <QuestionList
        loadQuestions={() =>
          new Promise<QuestionBankPage>((complete) => {
            resolve = complete;
          })
        }
        loadSyllabus={async () => syllabus}
      />,
    );
    expect(screen.getByTestId("question-list-skeleton")).toBeVisible();
    await waitFor(() => expect(resolve).toBeTypeOf("function"));
    resolve(firstPage);
    expect(await screen.findByText("Explain evaporation.")).toBeVisible();
  });

  it("combines controls, resets pagination, and retains selected questions across pages", async () => {
    const user = userEvent.setup();
    const loadQuestions = vi.fn(async (filters: QuestionBankFilters) =>
      filters.page === 1 ? secondPage : firstPage,
    );
    render(
      <QuestionList
        loadQuestions={loadQuestions}
        loadSyllabus={async () => syllabus}
      />,
    );
    await screen.findByText("Explain evaporation.");

    await user.click(
      screen.getByRole("checkbox", { name: "Select SCI-WATER-001" }),
    );
    expect(screen.getByText("1 selected")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(
      await screen.findByText("Choose the energy conversion."),
    ).toBeVisible();
    expect(screen.getByText("1 selected")).toBeVisible();
    await user.click(
      screen.getByRole("checkbox", { name: "Select SCI-ENERGY-001" }),
    );
    expect(screen.getByText("2 selected")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Open ended" }));
    await waitFor(() =>
      expect(loadQuestions).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 0, questionType: "OPEN_ENDED" }),
      ),
    );
    await user.click(screen.getByRole("button", { name: "Challenge" }));
    await waitFor(() =>
      expect(loadQuestions).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 0,
          questionType: "OPEN_ENDED",
          difficulty: "CHALLENGE",
        }),
      ),
    );
    await choose(user, "Subject", "Science");
    await choose(user, "Level", "Primary 5");
    await choose(user, "Theme", "Cycles");
    await choose(user, "Topic", "Water");
    await waitFor(() =>
      expect(loadQuestions).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 0,
          topicId: 14,
          questionType: "OPEN_ENDED",
        }),
      ),
    );
  });

  it("debounces a combined server search, resets pagination, and retains selections", async () => {
    const user = userEvent.setup();
    const loadQuestions = vi.fn(async (filters: QuestionBankFilters) =>
      filters.page === 1 ? secondPage : firstPage,
    );
    render(
      <QuestionList
        loadQuestions={loadQuestions}
        loadSyllabus={async () => syllabus}
      />,
    );
    await screen.findByText("Explain evaporation.");

    await user.click(
      screen.getByRole("checkbox", { name: "Select SCI-WATER-001" }),
    );
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(
      await screen.findByText("Choose the energy conversion."),
    ).toBeVisible();
    await user.click(
      screen.getByRole("checkbox", { name: "Select SCI-ENERGY-001" }),
    );
    expect(screen.getByText("2 selected")).toBeVisible();

    loadQuestions.mockClear();
    await user.type(screen.getByLabelText("Search questions"), "cafe");
    expect(loadQuestions).not.toHaveBeenCalled();
    await waitFor(() => expect(loadQuestions).toHaveBeenCalledTimes(1), {
      timeout: 1200,
    });
    expect(loadQuestions).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: "cafe", page: 0 }),
    );
    expect(screen.getByText("2 selected")).toBeVisible();
  });

  it("renders a responsive empty state and clears filters", async () => {
    const user = userEvent.setup();
    const loadQuestions = vi
      .fn()
      .mockResolvedValue({
        ...firstPage,
        items: [],
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      });
    render(
      <QuestionList
        loadQuestions={loadQuestions}
        loadSyllabus={async () => syllabus}
      />,
    );
    expect(
      await screen.findByRole("heading", {
        name: "No questions match these filters",
      }),
    ).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(loadQuestions).toHaveBeenCalledTimes(2);
  });

  it("shows a retryable service or invalid-payload error", async () => {
    const user = userEvent.setup();
    const loadQuestions = vi
      .fn()
      .mockRejectedValueOnce(
        new Error(
          "The learning service returned an invalid question page. Please try again.",
        ),
      )
      .mockResolvedValueOnce(firstPage);
    render(
      <QuestionList
        loadQuestions={loadQuestions}
        loadSyllabus={async () => syllabus}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "invalid question page",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading questions" }),
    );
    expect(await screen.findByText("Explain evaporation.")).toBeVisible();
  });

  it("links each question to its detail page while keeping the edit shortcut", async () => {
    render(
      <QuestionList
        loadQuestions={async () => firstPage}
        loadSyllabus={async () => syllabus}
      />,
    );
    expect(
      await screen.findByRole("link", { name: "View question" }),
    ).toHaveAttribute("href", "/questions/1");
    expect(screen.getByRole("link", { name: "Edit question" })).toHaveAttribute(
      "href",
      "/questions/1/edit",
    );
  });
});
