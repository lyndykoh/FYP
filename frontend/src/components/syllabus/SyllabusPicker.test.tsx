import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { SyllabusTree } from "@/services/syllabus";
import SyllabusPicker from "./SyllabusPicker";

const tree: SyllabusTree = {
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
                  code: "SCI_P5_CYCLES_WATER",
                  name: "Water",
                  nodeType: "TOPIC",
                  parentId: 3,
                  children: [
                    {
                      id: 5,
                      code: "SCI_P5_CYCLES_WATER_EVAP",
                      name: "Evaporation",
                      nodeType: "SUBTOPIC",
                      parentId: 4,
                      children: [],
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
  ],
};

describe("SyllabusPicker", () => {
  it("loads a cascading hierarchy and returns a selected topic or subtopic ID", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <SyllabusPicker
        value={null}
        onChange={onChange}
        loadSyllabus={async () => tree}
        required
      />,
    );
    expect(screen.getByRole("status")).toHaveTextContent("Loading syllabus");
    await choose(user, "Subject", "Science");
    await choose(user, "Level", "Primary 5");
    await choose(user, "Theme", "Cycles");
    await choose(user, "Topic", "Water");
    expect(onChange).toHaveBeenLastCalledWith(4);
    await choose(user, "Subtopic (optional)", "Evaporation");
    expect(onChange).toHaveBeenLastCalledWith(5);
  });

  it("preselects an existing leaf and exposes a recoverable load failure", async () => {
    const { rerender } = render(
      <SyllabusPicker
        value={5}
        onChange={vi.fn()}
        loadSyllabus={async () => tree}
      />,
    );
    expect(await screen.findByText("Evaporation")).toBeVisible();
    rerender(
      <SyllabusPicker
        value={null}
        onChange={vi.fn()}
        loadSyllabus={async () => {
          throw new Error("Syllabus offline");
        }}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Syllabus offline",
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Retry syllabus" }));
    await waitFor(() => expect(screen.getByRole("alert")).toBeVisible());
  });
});

async function choose(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) {
  await user.click(await screen.findByLabelText(label));
  await user.click(await screen.findByRole("option", { name: option }));
}
