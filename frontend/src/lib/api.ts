// frontend/lib/api.ts
import { saveAuthSession, type AuthResponsePayload } from "./auth";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8081";

interface ErrorResponseBody {
  error?: unknown;
  message?: unknown;
}

function getAuthorizationHeader(endpoint: string): HeadersInit {
  const token = localStorage.getItem("jwt_token");
  const isAuthenticationRoute = endpoint.startsWith("/api/auth/");

  if (isAuthenticationRoute || !token) {
    return {};
  }

  return { Authorization: `Bearer ${token}` };
}

function getJsonErrorMessage(body: string): string | null {
  try {
    const parsedBody = JSON.parse(body) as unknown;

    if (typeof parsedBody !== "object" || parsedBody === null) {
      return null;
    }

    const errorBody = parsedBody as ErrorResponseBody;
    if (typeof errorBody.message === "string") {
      return errorBody.message;
    }

    if (typeof errorBody.error === "string") {
      return errorBody.error;
    }
  } catch {
    // Fall back to the plain-text response below.
  }

  return null;
}

export async function apiRequest(endpoint: string, options: RequestInit = {}) {
  return fetch(`${API_URL}${endpoint}`, {
    headers: {
      "Content-Type": "application/json",
      ...getAuthorizationHeader(endpoint),
      ...options.headers,
    },
    ...options,
  });
}

export async function getErrorMessage(response: Response) {
  const contentType = response.headers.get("content-type") || "";
  const body = await response.text();

  if (contentType.includes("application/json")) {
    const errorMessage = getJsonErrorMessage(body);

    if (errorMessage) {
      return errorMessage;
    }
  }

  return body || `Request failed with status ${response.status}`;
}

export async function register(
  email: string,
  password: string,
  fullName: string,
) {
  const response = await apiRequest("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password, fullName, role: "STUDENT" }),
  });
  const jsonResponse = (await response.json()) as AuthResponsePayload;
  saveAuthSession(jsonResponse);
  return jsonResponse;
}
