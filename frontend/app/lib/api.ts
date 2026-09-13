// Google is the only sign-in method for now. The authorization endpoint is
// customized server-side (SecurityConfig) to live under /api/public/**, not
// Spring Security's default /oauth2/authorization/{id} - kept as a constant
// (not a fetch call) because the browser needs a full-page redirect here,
// not an XHR.
export const GOOGLE_LOGIN_URL = "/api/public/oauth2/authorization/google";

// Mirrors api's me.dhiren9939.api.common.ApiError - every failed response
// from the backend has exactly this shape.
export interface ApiError {
  status: number;
  code: string;
  message: string;
  fields?: Record<string, string[]>;
}

// Mirrors me.dhiren9939.api.common.ApiResponse<T> - every response, success
// or failure, is this envelope. Never trust a bare 2xx without checking
// `success`; the HTTP status and the envelope always agree, but `data` is
// only meaningful when `success` is true.
export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T | null;
  error: ApiError | null;
}

export interface User {
  userId: string;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
}

/** Thrown when a request fails even after a refresh attempt - callers redirect to login on this. */
export class UnauthenticatedError extends Error {
  constructor() {
    super("Not authenticated");
    this.name = "UnauthenticatedError";
  }
}

async function rawFetch<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
  const response = await fetch(path, {
    credentials: "include", // cookies ride along even if this ever runs cross-origin (no proxy) in prod
    headers: { Accept: "application/json", ...init?.headers },
    ...init,
  });
  return (await response.json()) as ApiResponse<T>;
}

// Refresh is one-shot (rotates the token) - two concurrent 401s must never each call refresh,
// or the second one presents an already-rotated cookie and gets treated as a replay, logging
// out every device. This shares a single in-flight refresh across every caller instead.
let refreshInFlight: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = rawFetch<void>("/api/public/auth/refresh", { method: "POST" })
      .then((res) => res.success)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/**
 * Fetches `path` as an authenticated call. On a 401 (expired access token) it transparently
 * refreshes once and retries; if the refresh itself fails, throws UnauthenticatedError instead
 * of surfacing a confusing second 401 - callers only need to handle one failure case.
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let response = await fetch(path, {
    credentials: "include",
    headers: { Accept: "application/json", ...init?.headers },
    ...init,
  });

  if (response.status === 401) {
    const refreshed = await refreshSession();
    if (!refreshed) {
      throw new UnauthenticatedError();
    }
    response = await fetch(path, {
      credentials: "include",
      headers: { Accept: "application/json", ...init?.headers },
      ...init,
    });
  }

  const body = (await response.json()) as ApiResponse<T>;
  if (!body.success || body.data === null) {
    throw new UnauthenticatedError();
  }
  return body.data;
}

export function fetchMe(): Promise<User> {
  return apiFetch<User>("/api/me");
}

export async function logout(): Promise<void> {
  await rawFetch<void>("/api/public/auth/logout", { method: "POST" });
}
