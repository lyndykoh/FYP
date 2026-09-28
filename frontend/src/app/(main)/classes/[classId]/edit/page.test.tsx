import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  fetchTutorClasses: vi.fn(),
  updateTutorClass: vi.fn(),
  push: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ classId: "12" }),
  useRouter: () => ({ push: mocks.push }),
}));

vi.mock("@/services/classes", () => ({
  fetchTutorClasses: mocks.fetchTutorClasses,
  updateTutorClass: mocks.updateTutorClass,
}));

vi.mock("@/components/classes/ClassForm", () => ({
  default: ({ onComplete }: { onComplete: () => void }) => (
    <button type="button" onClick={onComplete}>
      Save changes
    </button>
  ),
}));

vi.mock("@/components/classes/ClassStudentSelector", () => ({
  default: ({ onStudentAdded }: { onStudentAdded?: () => void }) => (
    <button type="button" onClick={onStudentAdded}>
      Add existing Student
    </button>
  ),
}));

import EditClassPage from "./page";

const savedClass = {
  id: 12,
  tutorId: 7,
  className: "Primary 5 Science",
  subject: "Science",
  level: "Primary 5",
  status: "ACTIVE" as const,
  schedules: [],
};

describe("EditClassPage", () => {
  it("returns to the freshly loaded class detail roster after an existing Student is added", async () => {
    const user = userEvent.setup();
    mocks.fetchTutorClasses.mockResolvedValue([savedClass]);
    render(<EditClassPage />);

    await screen.findByRole("button", { name: "Add existing Student" });
    await user.click(
      screen.getByRole("button", { name: "Add existing Student" }),
    );

    await waitFor(() => expect(mocks.push).toHaveBeenCalledWith("/classes/12"));
  });
});
