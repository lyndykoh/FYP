import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  fetchSyllabusTree,
  parseSyllabusTree,
  SyllabusApiError,
} from "./syllabus";

const tree = {
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

describe("syllabus service", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn()));

  it("validates and loads the complete ordered taxonomy tree", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(tree), { status: 200 }),
    );
    await expect(fetchSyllabusTree()).resolves.toEqual(tree);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/learning/shared/syllabus/tree"),
      expect.anything(),
    );
  });

  it("rejects malformed hierarchy payloads and preserves structured service errors", async () => {
    expect(() =>
      parseSyllabusTree({ items: [{ ...tree.items[0], nodeType: "TOPIC" }] }),
    ).toThrow(/invalid syllabus tree/i);
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: "Syllabus is unavailable." }), {
        status: 503,
      }),
    );
    await expect(fetchSyllabusTree()).rejects.toEqual(
      expect.objectContaining({
        name: "SyllabusApiError",
        status: 503,
        message: "Syllabus is unavailable.",
      } satisfies Partial<SyllabusApiError>),
    );
  });
});
