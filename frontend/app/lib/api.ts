// Google is the only sign-in method for now. The authorization endpoint is
// customized server-side (SecurityConfig) to live under /api/v1/public/**, not
// Spring Security's default /oauth2/authorization/{id} - kept as a constant
// (not a fetch call) because the browser needs a full-page redirect here,
// not an XHR.
export const GOOGLE_LOGIN_URL = "/api/v1/public/oauth2/authorization/google";

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
    refreshInFlight = rawFetch<void>("/api/v1/public/auth/refresh", { method: "POST" })
      .then((res) => res.success)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/** Thrown by `mutate` on a business failure (validation, not-found, ...) - never on an auth failure, which throws UnauthenticatedError instead. Carries the server's ApiError so forms can show field-level messages. */
export class ApiRequestError extends Error {
  constructor(public readonly apiError: ApiError | null) {
    super(apiError?.message ?? "Request failed");
    this.name = "ApiRequestError";
  }
}

/**
 * Fetches `path` as an authenticated call and returns the full envelope. On a 401 (expired
 * access token) it transparently refreshes once and retries; if the refresh itself fails, throws
 * UnauthenticatedError instead of surfacing a confusing second 401 - callers only need to handle
 * one failure case for "the session is gone". Never throws for a business-level failure (4xx with
 * a valid session) - callers inspect `success`/`error` themselves.
 */
async function apiFetchEnvelope<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
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

  return (await response.json()) as ApiResponse<T>;
}

/** Like `apiFetchEnvelope`, but for callers that only want the happy path - throws UnauthenticatedError on any failure, business or auth. Fine for reads where there's nothing useful to show without a session anyway; mutations that need to surface validation errors should use `mutate` instead. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const body = await apiFetchEnvelope<T>(path, init);
  if (!body.success || body.data === null) {
    throw new UnauthenticatedError();
  }
  return body.data;
}

/** For mutations: returns the response data on success, throws ApiRequestError (with the server's ApiError attached) on a business failure. */
async function mutate<T>(path: string, init?: RequestInit): Promise<T> {
  const body = await apiFetchEnvelope<T>(path, init);
  if (!body.success || body.data === null) {
    throw new ApiRequestError(body.error);
  }
  return body.data;
}

/** Same as `mutate`, but for endpoints with no response payload (data is legitimately null on success). */
async function mutateVoid(path: string, init?: RequestInit): Promise<void> {
  const body = await apiFetchEnvelope<void>(path, init);
  if (!body.success) {
    throw new ApiRequestError(body.error);
  }
}

export function fetchProfile(signal?: AbortSignal): Promise<User> {
  return apiFetch<User>("/api/v1/profile", { signal });
}

export async function logout(): Promise<void> {
  await rawFetch<void>("/api/v1/public/auth/logout", { method: "POST" });
}

// Mirrors me.dhiren9939.api.apikey.service.ApiKeyExpiry.Duration.
export type ApiKeyExpiry = "ONE_DAY" | "ONE_MONTH" | "NEVER";

// Mirrors me.dhiren9939.api.apikey.dto.ApiKeyDto - list items and patch results, never key material.
// There's no "enabled" concept server-side; a key is either present or deleted.
export interface ApiKey {
  apiKeyId: string;
  name: string;
  createdAt: string;
  expiresAt: string | null;
}

// Mirrors me.dhiren9939.api.apikey.dto.ApiKeyCreatedDto - the only response that ever
// carries the raw key, and only this once.
export interface ApiKeyCreated extends ApiKey {
  rawKey: string;
}

// Mirrors me.dhiren9939.api.apikey.dto.ApiKeyPageDto - page-number/size paging, not cursor-based.
export interface ApiKeyPage {
  apiKeyList: ApiKey[];
  pageNo: number;
  size: number;
  totalElements: number;
  numberOfPages: number;
}

export function listApiKeys(page = 0, size = 20, signal?: AbortSignal): Promise<ApiKeyPage> {
  return apiFetch<ApiKeyPage>(`/api/v1/apikeys?page=${page}&size=${size}`, { signal });
}

export function createApiKey(name: string, expiry: ApiKeyExpiry): Promise<ApiKeyCreated> {
  return mutate<ApiKeyCreated>("/api/v1/apikeys", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, expiry }),
  });
}

/** name/expiry are optional - omitted means "leave unchanged" (mirrors ApiKeyPatchRequest). */
export function patchApiKey(
  apiKeyId: string,
  patch: { name?: string; expiry?: ApiKeyExpiry }
): Promise<ApiKey> {
  return mutate<ApiKey>(`/api/v1/apikeys/${apiKeyId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch),
  });
}

export function deleteApiKey(apiKeyId: string): Promise<void> {
  return mutateVoid(`/api/v1/apikeys/${apiKeyId}`, { method: "DELETE" });
}

// Mirrors me.dhiren9939.api.tenants.dto.TenantDto.
export interface Tenant {
  tenantId: string;
  name: string;
}

// Mirrors me.dhiren9939.api.tenants.dto.TenantPageDto - page-number/size paging, not cursor-based.
export interface TenantPage {
  tenantList: Tenant[];
  pageNo: number;
  size: number;
  totalElements: number;
  numberOfPages: number;
}

export function listTenants(page = 0, size = 20, signal?: AbortSignal): Promise<TenantPage> {
  return apiFetch<TenantPage>(`/api/v1/tenants?page=${page}&size=${size}`, { signal });
}

export function getTenant(tenantId: string, signal?: AbortSignal): Promise<Tenant> {
  return apiFetch<Tenant>(`/api/v1/tenants/${tenantId}`, { signal });
}

export function createTenant(name: string): Promise<Tenant> {
  return mutate<Tenant>("/api/v1/tenants", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
}

export function patchTenant(tenantId: string, patch: { name?: string }): Promise<Tenant> {
  return mutate<Tenant>(`/api/v1/tenants/${tenantId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch),
  });
}

export function deleteTenant(tenantId: string): Promise<void> {
  return mutateVoid(`/api/v1/tenants/${tenantId}`, { method: "DELETE" });
}

// Mirrors me.dhiren9939.api.channels.dto.ChannelDto.
export interface Channel {
  channelId: string;
  tenantId: string;
  callBackUrl: string;
}

// Mirrors me.dhiren9939.api.channels.dto.ChannelPageDto - page-number/size paging, not cursor-based.
export interface ChannelPage {
  channelList: Channel[];
  pageNo: number;
  size: number;
  totalElements: number;
  numberOfPages: number;
}

// Mirrors me.dhiren9939.api.channels.dto.ChannelEventTypeDto.
export interface ChannelEventType {
  channelId: string;
  eventType: string;
}

export function listChannels(tenantId: string, page = 0, size = 20, signal?: AbortSignal): Promise<ChannelPage> {
  return apiFetch<ChannelPage>(`/api/v1/tenants/${tenantId}/channels?page=${page}&size=${size}`, { signal });
}

export function createChannel(tenantId: string, callBackUrl: string): Promise<Channel> {
  return mutate<Channel>(`/api/v1/tenants/${tenantId}/channels`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ callBackUrl }),
  });
}

export function patchChannel(tenantId: string, channelId: string, patch: { callBackUrl?: string }): Promise<Channel> {
  return mutate<Channel>(`/api/v1/tenants/${tenantId}/channels/${channelId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch),
  });
}

export function deleteChannel(tenantId: string, channelId: string): Promise<void> {
  return mutateVoid(`/api/v1/tenants/${tenantId}/channels/${channelId}`, { method: "DELETE" });
}

export function listChannelEventTypes(
  tenantId: string,
  channelId: string,
  signal?: AbortSignal
): Promise<ChannelEventType[]> {
  return apiFetch<ChannelEventType[]>(`/api/v1/tenants/${tenantId}/channels/${channelId}/event-types`, { signal });
}

export function addChannelEventType(tenantId: string, channelId: string, eventType: string): Promise<ChannelEventType> {
  return mutate<ChannelEventType>(`/api/v1/tenants/${tenantId}/channels/${channelId}/event-types`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ eventType }),
  });
}

export function removeChannelEventType(tenantId: string, channelId: string, eventType: string): Promise<void> {
  return mutateVoid(
    `/api/v1/tenants/${tenantId}/channels/${channelId}/event-types/${encodeURIComponent(eventType)}`,
    { method: "DELETE" }
  );
}
