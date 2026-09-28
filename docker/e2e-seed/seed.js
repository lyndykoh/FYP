const http = require("node:http");

const auth = "http://auth-service:8081";
const learning = "http://learning-service:8083";
const tutor = {
  email: "e2e.tutor@example.test",
  password: "E2eTutor!Pass123",
};
const student = {
  email: "e2e.student@example.test",
  password: "E2eStudent!Pass123",
};

const JSON_HEADERS = { "content-type": "application/json" };
const MAXIMUM_SERVICE_READINESS_ATTEMPTS = 60;
const SERVICE_READINESS_DELAY_MS = 1000;

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function parseResponsePayload(response, description) {
  const body = await response.text();

  if (!response.ok) {
    throw new Error(`${description} failed (${response.status}): ${body}`);
  }

  return body ? JSON.parse(body) : null;
}

async function request(url, description, options = {}) {
  const response = await fetch(url, options);
  return parseResponsePayload(response, description);
}

function createJsonPostRequest(token, body, idempotencyKey) {
  const headers = {
    ...JSON_HEADERS,
    authorization: `Bearer ${token}`,
  };

  if (idempotencyKey) {
    headers["idempotency-key"] = idempotencyKey;
  }

  return {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  };
}

function decodeUserId(token) {
  const encodedPayload = token.split(".")[1];
  const tokenPayload = Buffer.from(encodedPayload, "base64url").toString("utf8");

  return JSON.parse(tokenPayload).userId;
}

async function eventually(work, description) {
  let lastError;

  for (let attempt = 0; attempt < MAXIMUM_SERVICE_READINESS_ATTEMPTS; attempt += 1) {
    try {
      return await work();
    } catch (error) {
      lastError = error;
      await wait(SERVICE_READINESS_DELAY_MS);
    }
  }

  const errorDescription =
    lastError instanceof Error ? lastError.message : String(lastError);

  throw new Error(`${description} did not become ready: ${errorDescription}`);
}

function firstQuestionNode(nodes) {
  for (const node of nodes) {
    const isQuestionContainer =
      node.nodeType === "TOPIC" || node.nodeType === "SUBTOPIC";

    if (isQuestionContainer) {
      return node;
    }

    const child = firstQuestionNode(node.children || []);

    if (child) {
      return child;
    }
  }

  return null;
}

async function login(credentials) {
  return request(`${auth}/api/auth/login`, `login ${credentials.email}`, {
    method: "POST",
    headers: JSON_HEADERS,
    body: JSON.stringify(credentials),
  });
}

async function createE2eClass(tutorToken) {
  return request(
    `${learning}/api/learning/tutor/classes`,
    "create E2E class",
    createJsonPostRequest(tutorToken, {
      className: "E2E Science Class",
      subject: "Science",
      level: "Primary 5",
      status: "ACTIVE",
      schedules: [],
    }),
  );
}

async function createLinkedE2eStudent(tutorToken, studentToken, classId) {
  return request(
    `${learning}/api/learning/tutor/students`,
    "create linked E2E student",
    createJsonPostRequest(tutorToken, {
      fullName: "E2E Student",
      loginUserId: decodeUserId(studentToken),
      classIds: [classId],
    }),
  );
}

async function loadFirstSyllabusTopic(tutorToken) {
  const syllabusTree = await request(
    `${learning}/api/learning/shared/syllabus/tree`,
    "load syllabus",
    { headers: { authorization: `Bearer ${tutorToken}` } },
  );
  const topic = firstQuestionNode(syllabusTree.items);

  if (!topic) {
    throw new Error("Seeded syllabus contains no TOPIC or SUBTOPIC node.");
  }

  return topic;
}

async function createE2eQuestion(tutorToken, topicId) {
  return request(
    `${learning}/api/learning/tutor/questions`,
    "create E2E question",
    createJsonPostRequest(tutorToken, {
      code: "E2E-SCI-001",
      syllabusTopicId: topicId,
      questionType: "SHORT_ANSWER",
      prompt: "Explain why water evaporates on a hot day.",
      totalMarks: 2,
      modelAnswer: "Water gains energy and changes into water vapour.",
      archiveState: "ACTIVE",
      markingComponents: [
        {
          description: "States that water gains energy",
          marks: 2,
          keywords: ["energy"],
        },
      ],
      keywords: ["energy"],
    }),
  );
}

async function generateE2eWorksheet(tutorToken, classId, studentId, topicId) {
  return request(
    `${learning}/api/learning/tutor/classes/${classId}/worksheet-generation-requests`,
    "generate E2E worksheet",
    createJsonPostRequest(
      tutorToken,
      {
        targetMode: "STUDENTS",
        studentIds: [studentId],
        topicIds: [topicId],
        questionCount: 1,
        title: "E2E Evaporation Worksheet",
        instructions: "Explain your answer using the word energy.",
      },
      "e2e-seed-standard-worksheet",
    ),
  );
}

async function approveE2eWorksheet(tutorToken, worksheetId) {
  return request(
    `${learning}/api/learning/tutor/worksheets/${worksheetId}/approve`,
    "approve E2E worksheet",
    createJsonPostRequest(tutorToken, {}),
  );
}

function createScenario({
  tutorSession,
  studentSession,
  classItem,
  studentItem,
  topic,
  question,
  worksheet,
}) {
  return {
    tutor,
    student,
    classId: classItem.id,
    studentId: studentItem.id,
    topicId: topic.id,
    questionId: question.id,
    worksheetId: worksheet.id,
    tutorToken: tutorSession.token,
    studentToken: studentSession.token,
  };
}

async function seed() {
  await eventually(() => request(`${auth}/actuator/health`, "auth health"), "auth service");
  await eventually(() => request(`${learning}/actuator/health`, "learning health"), "learning service");

  const tutorSession = await eventually(() => login(tutor), "bootstrap tutor");
  let studentSession;

  try {
    studentSession = await request(`${auth}/api/auth/register`, "register E2E student", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify({ ...student, fullName: "E2E Student", role: "STUDENT" }),
    });
  } catch (error) {
    studentSession = await login(student);
  }

  const tutorToken = tutorSession.token;
  const studentToken = studentSession.token;
  const classItem = await createE2eClass(tutorToken);
  const studentItem = await createLinkedE2eStudent(
    tutorToken,
    studentToken,
    classItem.id,
  );
  const topic = await loadFirstSyllabusTopic(tutorToken);
  const question = await createE2eQuestion(tutorToken, topic.id);
  const generated = await generateE2eWorksheet(
    tutorToken,
    classItem.id,
    studentItem.id,
    topic.id,
  );

  if (!generated.worksheet?.id) {
    throw new Error(`Seed worksheet was not generated: ${JSON.stringify(generated)}`);
  }

  const worksheet = await approveE2eWorksheet(tutorToken, generated.worksheet.id);

  return createScenario({
    tutorSession,
    studentSession,
    classItem,
    studentItem,
    topic,
    question,
    worksheet,
  });
}

let scenario = null;
let failure = null;

seed()
  .then((result) => {
    scenario = result;
  })
  .catch((error) => {
    failure = error instanceof Error ? error.message : String(error);
  });

function sendSeedServiceResponse(reply, statusCode, responseBody) {
  reply.writeHead(statusCode, JSON_HEADERS);
  reply.end(JSON.stringify(responseBody));
}

function getHealthCheckResponse() {
  if (scenario) {
    return { statusCode: 200, body: { status: "ready" } };
  }

  return {
    statusCode: 503,
    body: { status: "starting", error: failure },
  };
}

function handleSeedServiceRequest(request, reply) {
  if (request.url === "/health") {
    const healthCheckResponse = getHealthCheckResponse();

    return sendSeedServiceResponse(
      reply,
      healthCheckResponse.statusCode,
      healthCheckResponse.body,
    );
  }

  if (request.url === "/context" && scenario) {
    // Tokens are intentionally omitted: browser tests authenticate through the UI.
    const { tutorToken, studentToken, ...safeScenario } = scenario;

    return sendSeedServiceResponse(reply, 200, safeScenario);
  }

  reply.writeHead(404);
  return reply.end();
}

http.createServer(handleSeedServiceRequest).listen(8090, "0.0.0.0");
