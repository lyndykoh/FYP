export type AuthRole = "TUTOR" | "STUDENT";

export interface AuthSession {
  token: string;
  email: string;
  role: AuthRole;
  expiresAt: number;
}

export interface AuthResponsePayload {
  token: string;
  email: string;
  role: AuthRole;
}

interface TokenClaims {
  sub?: unknown;
  role?: unknown;
  exp?: unknown;
}

const TUTOR_PATHS = [
  "/tutor/dashboard",
  "/tutor/worksheets",
  "/tutor/reviews",
  "/tutor/alerts",
  "/classes",
  "/students",
  "/questions",
  "/reports",
  "/upload",
  "/ocr",
  "/manual-answers",
  "/profile",
];
const STUDENT_PATHS = [
  "/",
  "/student/dashboard",
  "/worksheets",
  "/upload",
  "/ocr",
  "/manual-answers",
  "/mistakes",
  "/progress",
  "/topics",
  "/subject-profile",
  "/reports",
  "/profile",
];

function decodePayload(token: string): TokenClaims | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) {
      return null;
    }

    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const payloadLength = Math.ceil(normalized.length / 4) * 4;
    const padded = normalized.padEnd(payloadLength, "=");

    return JSON.parse(globalThis.atob(padded)) as TokenClaims;
  } catch {
    return null;
  }
}

export function parseAuthToken(
  token: string,
  nowMs = Date.now(),
): AuthSession | null {
  const claims = decodePayload(token);
  if (
    !claims ||
    typeof claims.sub !== "string" ||
    !claims.sub ||
    (claims.role !== "TUTOR" && claims.role !== "STUDENT") ||
    typeof claims.exp !== "number" ||
    claims.exp * 1000 <= nowMs
  ) {
    return null;
  }

  return {
    token,
    email: claims.sub,
    role: claims.role,
    expiresAt: claims.exp * 1000,
  };
}

export function getRoleHome(role: AuthRole): string {
  return role === "TUTOR" ? "/tutor/dashboard" : "/student/dashboard";
}

export function isPathAllowed(role: AuthRole, pathname: string): boolean {
  const allowedPaths = role === "TUTOR" ? TUTOR_PATHS : STUDENT_PATHS;
  return allowedPaths.some((path) =>
    path === "/"
      ? pathname === "/"
      : pathname === path || pathname.startsWith(`${path}/`),
  );
}

export function saveAuthSession(payload: AuthResponsePayload): AuthSession {
  const session = parseAuthToken(payload.token);
  if (
    !session ||
    session.role !== payload.role ||
    session.email !== payload.email
  ) {
    throw new Error("Authentication service returned an invalid session");
  }
  if (typeof window === "undefined") {
    throw new Error("Browser storage is unavailable");
  }

  window.localStorage.setItem("jwt_token", session.token);
  const maxAge = Math.max(
    0,
    Math.floor((session.expiresAt - Date.now()) / 1000),
  );
  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `auth_token=${session.token}; path=/; max-age=${maxAge}; SameSite=Lax${secure}`;
  return session;
}

export function getBrowserSession(): AuthSession | null {
  if (typeof window === "undefined") {
    return null;
  }

  const token = window.localStorage.getItem("jwt_token");
  if (!token) {
    return null;
  }

  const session = parseAuthToken(token);
  if (!session) {
    clearAuthSession();
  }

  return session;
}

export function clearAuthSession(): void {
  if (typeof window !== "undefined") {
    window.localStorage.removeItem("jwt_token");
  }
  if (typeof document !== "undefined") {
    document.cookie = "auth_token=; path=/; max-age=0; SameSite=Lax";
  }
}
