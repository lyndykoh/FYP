// Mock data for the student-facing Lumina pages. Swap for real API calls later.

export const accent = "rgb(230,126,102)";

export type WorksheetStatus = "completed" | "incomplete";

export interface Worksheet {
  id: string;
  title: string;
  topic: string;
  assigned: string;
  submitted: string | null;
  score: number | null;
  status: WorksheetStatus;
}

export const worksheets: Worksheet[] = [
  {
    id: "ws5",
    title: "Simple Electrical Circuits",
    topic: "Electrical Systems",
    assigned: "22 Jul 2026",
    submitted: null,
    score: null,
    status: "incomplete",
  },
  {
    id: "ws2",
    title: "Forces and Motion Basics",
    topic: "Forces & Energy",
    assigned: "21 Jul 2026",
    submitted: null,
    score: null,
    status: "incomplete",
  },
  {
    id: "ws1",
    title: "The Water Cycle & Evaporation",
    topic: "Water Cycle",
    assigned: "18 Jul 2026",
    submitted: "20 Jul 2026",
    score: 82,
    status: "completed",
  },
  {
    id: "ws3",
    title: "Photosynthesis & Plant Systems",
    topic: "Plant Systems",
    assigned: "10 Jul 2026",
    submitted: "12 Jul 2026",
    score: 68,
    status: "completed",
  },
  {
    id: "ws4",
    title: "Properties of Materials",
    topic: "Materials",
    assigned: "05 Jul 2026",
    submitted: "06 Jul 2026",
    score: 91,
    status: "completed",
  },
];

export type QuestionStatus = "correct" | "partial" | "incorrect" | "tutor";

export interface Question {
  id: string;
  n: number;
  status: QuestionStatus;
  mark: number | null;
  max: number;
  topic: string;
  question: string;
  student: string;
  correct: string;
  tags: string[];
  summary: string;
  what?: string;
  why?: string;
  how?: string;
  remember?: string;
  next?: string;
}

export const questions: Question[] = [
  {
    id: "q1",
    n: 1,
    status: "correct",
    mark: 2,
    max: 2,
    topic: "Water Cycle",
    question: "Name the process where water turns from a liquid into a gas.",
    student: "Evaporation",
    correct: "Evaporation",
    tags: [],
    summary: "Correct — clear and accurate.",
  },
  {
    id: "q2",
    n: 2,
    status: "correct",
    mark: 2,
    max: 2,
    topic: "Water Cycle",
    question: "What happens to water vapour when it cools high in the sky?",
    student: "It condenses into tiny water droplets and forms clouds.",
    correct: "It condenses to form clouds.",
    tags: [],
    summary: "Correct — you included the extra detail about droplets.",
  },
  {
    id: "q3",
    n: 3,
    status: "incorrect",
    mark: 0,
    max: 3,
    topic: "Water Cycle",
    question: "Explain why puddles disappear faster on a hot, windy day.",
    student: "Because the sun melts the water away into the ground.",
    correct:
      "Heat gives water molecules more energy so they evaporate faster, and wind carries the water vapour away, speeding up evaporation.",
    tags: ["Concept misunderstanding"],
    summary: "The puddle evaporates — it doesn't melt or soak away.",
    what: "You said the sun 'melts' the water and it goes into the ground.",
    why: "Melting is solid → liquid (like ice to water). A puddle is already liquid, so it can't melt. It disappears by evaporation — turning into water vapour.",
    how: "Heat gives the water more energy to evaporate, and wind blows the vapour away so more can escape. Both make the puddle vanish faster.",
    remember:
      "Puddles disappear by evaporation, not melting. Heat + wind = faster evaporation.",
    next: "Try the practice set on the Water Cycle to lock this in.",
  },
  {
    id: "q4",
    n: 4,
    status: "partial",
    mark: 1,
    max: 2,
    topic: "Water Cycle",
    question: "Draw and label the water cycle. Include two labelled stages.",
    student: "Labelled evaporation and rain.",
    correct: "Evaporation, condensation, precipitation (and collection).",
    tags: ["Incomplete working", "Missing key point"],
    summary: "Right start — but 'condensation' was missing between them.",
    what: "You labelled evaporation and rain (precipitation) correctly.",
    why: "You skipped condensation — the stage where vapour cools into clouds — which comes between evaporation and rain.",
    how: "Order the cycle: evaporation → condensation → precipitation → collection. Add condensation between your two labels.",
    remember:
      "The water cycle has four stages. Condensation makes the clouds before it rains.",
    next: "Review the labelled diagram in Topic: Water Cycle.",
  },
  {
    id: "q5",
    n: 5,
    status: "incorrect",
    mark: 0,
    max: 2,
    topic: "Water Cycle",
    question:
      "A beaker holds 250 of water. Record the volume with the correct unit.",
    student: "250 kg",
    correct: "250 ml (or 250 cm³)",
    tags: ["Wrong units"],
    summary: "Right number, wrong unit — kg measures mass, not volume.",
    what: "You wrote 250 kg.",
    why: "Kilograms (kg) measure mass — how heavy something is. Volume (how much space liquid takes up) is measured in millilitres (ml) or cm³.",
    how: "For liquids in a beaker, use ml or cm³. So the answer is 250 ml.",
    remember: "Mass → grams/kilograms. Volume of liquid → millilitres.",
    next: "Practice the units check in Topic: Measurement.",
  },
  {
    id: "q6",
    n: 6,
    status: "tutor",
    mark: null,
    max: 3,
    topic: "Water Cycle",
    question:
      "In your own words, describe how clouds are important to living things.",
    student:
      "Clouds give us rain and they give shade so it is not too hot and plants and animals need water to live and grow.",
    correct: "Open response — tutor review.",
    tags: ["Weak explanation"],
    summary:
      "Good ideas — your tutor will give personalised feedback on this one.",
    what: "You mentioned rain, shade, and that living things need water.",
    why: "This is an open, written-answer question. The AI isn't confident marking longer explanations, so it has been sent to your tutor.",
    how: "Your tutor will read this and reply with feedback soon.",
    remember: "Longer written answers are checked by a real tutor.",
    next: "You'll get a notification when your tutor replies.",
  },
];

export interface UnderstandingOption {
  key: string;
  label: string;
}

export const understandingOptions: UnderstandingOption[] = [
  { key: "not_reviewed", label: "Not Reviewed" },
  { key: "reviewed", label: "Reviewed" },
  { key: "attempted", label: "Correction Attempted" },
  { key: "understood", label: "Understood" },
  { key: "help", label: "Still Need Help" },
];

export const statusMeta: Record<
  QuestionStatus,
  { label: string; bg: string; fg: string; dot: string }
> = {
  correct: {
    label: "Correct",
    bg: "rgb(233,238,233)",
    fg: "rgb(70,92,70)",
    dot: "rgb(138,154,138)",
  },
  partial: {
    label: "Partially Correct",
    bg: "rgb(248,240,225)",
    fg: "rgb(140,105,45)",
    dot: "rgb(194,155,98)",
  },
  incorrect: {
    label: "Incorrect",
    bg: "rgb(248,232,226)",
    fg: "rgb(155,68,48)",
    dot: "rgb(155,68,48)",
  },
  tutor: {
    label: "Tutor Review Needed",
    bg: "rgb(232,226,217)",
    fg: "rgb(77,69,64)",
    dot: "rgb(126,117,111)",
  },
};

export type MasteryStatus =
  | "mastered"
  | "improving"
  | "practising"
  | "learning"
  | "needs_revision"
  | "not_started"
  | "locked";

export const masteryMeta: Record<
  MasteryStatus,
  { label: string; bg: string; fg: string; dot: string; bar: string }
> = {
  mastered: {
    label: "Mastered",
    bg: "rgb(233,238,233)",
    fg: "rgb(70,92,70)",
    dot: "rgb(138,154,138)",
    bar: "rgb(138,154,138)",
  },
  improving: {
    label: "Improving",
    bg: "rgb(233,238,233)",
    fg: "rgb(70,92,70)",
    dot: "rgb(138,154,138)",
    bar: "rgb(138,154,138)",
  },
  practising: {
    label: "Practising",
    bg: "rgb(248,240,225)",
    fg: "rgb(140,105,45)",
    dot: "rgb(194,155,98)",
    bar: "rgb(194,155,98)",
  },
  learning: {
    label: "Learning",
    bg: "rgb(248,240,225)",
    fg: "rgb(140,105,45)",
    dot: "rgb(194,155,98)",
    bar: "rgb(194,155,98)",
  },
  needs_revision: {
    label: "Needs Revision",
    bg: "rgb(248,232,226)",
    fg: "rgb(155,68,48)",
    dot: "rgb(155,68,48)",
    bar: "rgb(155,68,48)",
  },
  not_started: {
    label: "Not Started",
    bg: "rgb(232,226,217)",
    fg: "rgb(77,69,64)",
    dot: "rgb(126,117,111)",
    bar: "rgb(207,196,189)",
  },
  locked: {
    label: "Locked",
    bg: "rgb(232,226,217)",
    fg: "rgb(126,117,111)",
    dot: "rgb(126,117,111)",
    bar: "rgb(207,196,189)",
  },
};

export interface Topic {
  id: string;
  name: string;
  desc: string;
  status: MasteryStatus;
  completion: number;
  latestScore: number | null;
  attempts: number;
  accuracy: number | null;
  lastPractised: string;
  related: number;
  recWs: string | null;
  recWsId: string | null;
  locked: boolean;
  skills: string[];
  understood: string[];
  practice: string[];
  trend: number[];
  teacher: string;
}

export const topics: Topic[] = [
  {
    id: "t1",
    name: "The Water Cycle",
    desc: "Evaporation, condensation and how water moves around the Earth.",
    status: "improving",
    completion: 82,
    latestScore: 82,
    attempts: 3,
    accuracy: 78,
    lastPractised: "20 Jul 2026",
    related: 2,
    recWs: "Water Cycle Practice Set",
    recWsId: "ws2",
    locked: false,
    skills: [
      "Evaporation & condensation",
      "Naming the four stages",
      "Reading water-cycle diagrams",
    ],
    understood: ["Naming the stages in order", "Identifying evaporation"],
    practice: ["Melting vs. evaporation", "Choosing the right units"],
    trend: [55, 60, 72, 78, 82],
    teacher:
      "Lovely progress this term, Bella — your diagrams are much clearer now.",
  },
  {
    id: "t2",
    name: "Forces & Energy",
    desc: "Pushes, pulls, friction and how objects move.",
    status: "learning",
    completion: 40,
    latestScore: null,
    attempts: 1,
    accuracy: 64,
    lastPractised: "21 Jul 2026",
    related: 1,
    recWs: "Forces and Motion Basics",
    recWsId: "ws2",
    locked: false,
    skills: ["Types of forces", "Friction", "Balanced vs. unbalanced forces"],
    understood: ["Identifying pushes and pulls"],
    practice: [
      "Telling mass and weight apart",
      "Friction in everyday examples",
    ],
    trend: [50, 58, 64],
    teacher: "",
  },
  {
    id: "t3",
    name: "Plant Systems",
    desc: "Photosynthesis, parts of a plant and how plants make food.",
    status: "needs_revision",
    completion: 55,
    latestScore: 68,
    attempts: 2,
    accuracy: 61,
    lastPractised: "12 Jul 2026",
    related: 2,
    recWs: "Photosynthesis Refresher",
    recWsId: "ws3",
    locked: false,
    skills: [
      "Photosynthesis word equation",
      "Parts of a leaf",
      "Role of sunlight & water",
    ],
    understood: ["Naming plant parts"],
    practice: ["The photosynthesis equation", "What plants release and absorb"],
    trend: [70, 66, 61],
    teacher: "Let's revisit photosynthesis together in our next session.",
  },
  {
    id: "t4",
    name: "Properties of Materials",
    desc: "Comparing materials and choosing them for a purpose.",
    status: "mastered",
    completion: 100,
    latestScore: 91,
    attempts: 2,
    accuracy: 93,
    lastPractised: "06 Jul 2026",
    related: 2,
    recWs: "Materials Challenge",
    recWsId: "ws4",
    locked: false,
    skills: [
      "Comparing properties",
      "Matching material to use",
      "Conductors & insulators",
    ],
    understood: ["Comparing properties", "Matching material to use"],
    practice: [],
    trend: [80, 86, 91],
    teacher: "Excellent work — this is a real strength for you.",
  },
  {
    id: "t5",
    name: "Electrical Systems",
    desc: "Simple circuits, switches and what makes a bulb light up.",
    status: "not_started",
    completion: 0,
    latestScore: null,
    attempts: 0,
    accuracy: null,
    lastPractised: "—",
    related: 1,
    recWs: "Simple Electrical Circuits",
    recWsId: "ws5",
    locked: false,
    skills: [
      "Building a simple circuit",
      "Switches",
      "Complete vs. broken circuits",
    ],
    understood: [],
    practice: [],
    trend: [],
    teacher: "",
  },
  {
    id: "t6",
    name: "The Human Body",
    desc: "Body systems and how they keep us healthy.",
    status: "locked",
    completion: 0,
    latestScore: null,
    attempts: 0,
    accuracy: null,
    lastPractised: "—",
    related: 0,
    recWs: null,
    recWsId: null,
    locked: true,
    skills: ["Digestive system", "Circulatory system"],
    understood: [],
    practice: [],
    trend: [],
    teacher: "",
  },
];

export interface Mistake {
  id: string;
  topic: string;
  worksheet: string;
  date: string;
  cat: string;
  repeated: boolean;
  statusKey: string;
  question: string;
  student: string;
  correct: string;
  explanation: string;
}

export const mistakes: Mistake[] = [
  {
    id: "m1",
    topic: "The Water Cycle",
    worksheet: "Water Cycle & Evaporation",
    date: "20 Jul 2026",
    cat: "Concept misunderstanding",
    repeated: false,
    statusKey: "not_reviewed",
    question: "Explain why puddles disappear faster on a hot, windy day.",
    student: "Because the sun melts the water away into the ground.",
    correct:
      "Heat and wind speed up evaporation — the water turns into vapour and blows away.",
    explanation:
      "A puddle is already liquid, so it can't melt. It disappears by evaporation. Heat gives more energy and wind carries the vapour away.",
  },
  {
    id: "m2",
    topic: "The Water Cycle",
    worksheet: "Water Cycle & Evaporation",
    date: "20 Jul 2026",
    cat: "Wrong units",
    repeated: true,
    statusKey: "help",
    question: "Record the volume of water in the beaker with the correct unit.",
    student: "250 kg",
    correct: "250 ml (or 250 cm³)",
    explanation:
      "Kilograms measure mass, not volume. Liquid volume is measured in millilitres (ml) or cm³.",
  },
  {
    id: "m3",
    topic: "Plant Systems",
    worksheet: "Photosynthesis & Plant Systems",
    date: "12 Jul 2026",
    cat: "Missing key point",
    repeated: true,
    statusKey: "reviewed",
    question: "Write the word equation for photosynthesis.",
    student: "sunlight + water → food",
    correct: "carbon dioxide + water → (using light) glucose + oxygen",
    explanation:
      "You missed carbon dioxide and oxygen. Light is the energy source, not an ingredient in the equation itself.",
  },
  {
    id: "m4",
    topic: "Forces & Energy",
    worksheet: "Forces and Motion Basics",
    date: "21 Jul 2026",
    cat: "Concept misunderstanding",
    repeated: false,
    statusKey: "attempted",
    question: "What is the difference between mass and weight?",
    student: "They are the same thing measured in kg.",
    correct:
      "Mass is the amount of matter (kg); weight is the pull of gravity on that mass (newtons, N).",
    explanation:
      "Mass stays the same everywhere; weight changes with gravity. They use different units.",
  },
  {
    id: "m5",
    topic: "Plant Systems",
    worksheet: "Photosynthesis & Plant Systems",
    date: "12 Jul 2026",
    cat: "Careless mistake",
    repeated: false,
    statusKey: "understood",
    question: "Label the part of the plant that absorbs water.",
    student: "Leaves",
    correct: "Roots",
    explanation:
      "A small slip — roots absorb water from the soil; leaves make food using light.",
  },
];

export interface SkillProfileEntry {
  label: string;
  value: number;
}

export const skillProfile: SkillProfileEntry[] = [
  { label: "Conceptual understanding", value: 82 },
  { label: "Calculation accuracy", value: 74 },
  { label: "Knowledge application", value: 68 },
  { label: "Problem-solving", value: 71 },
  { label: "Working & presentation", value: 60 },
  { label: "Explanation quality", value: 55 },
  { label: "Question interpretation", value: 66 },
  { label: "Exam-answering technique", value: 63 },
];

export interface MistakeTypeBreakdownEntry {
  label: string;
  count: number;
}

export const mistakeTypeBreakdown: MistakeTypeBreakdownEntry[] = [
  { label: "Concept misunderstanding", count: 4 },
  { label: "Wrong units", count: 3 },
  { label: "Missing key point", count: 2 },
  { label: "Careless mistake", count: 2 },
];

export const student = {
  name: "Bella Tan",
  grade: "P5",
  subject: "Science",
};

export function initialsOf(name: string): string {
  return name
    .split(" ")
    .map((w) => w[0])
    .slice(0, 2)
    .join("");
}

export function understandingMeta(key: string | undefined): {
  bg: string;
  fg: string;
} {
  if (key === "understood")
    return { bg: "rgb(233,238,233)", fg: "rgb(70,92,70)" };
  if (key === "help") return { bg: "rgb(248,232,226)", fg: "rgb(155,68,48)" };
  return { bg: "rgb(232,226,217)", fg: "rgb(77,69,64)" };
}

export function understandingLabel(key: string | undefined): string {
  return (
    understandingOptions.find((o) => o.key === key)?.label ?? "Not Reviewed"
  );
}
