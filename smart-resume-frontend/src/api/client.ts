import type { AuthResponse } from "./types";

const BASE: string = (import.meta.env.VITE_API_URL as string | undefined) ?? "";
const ACCESS_KEY = "srp.access";
const REFRESH_KEY = "srp.refresh";

/** RFC 7807 problem details from the backend, flattened. */
export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: Record<string, string>;
  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export const tokens = {
  get access() { return localStorage.getItem(ACCESS_KEY); },
  get refresh() { return localStorage.getItem(REFRESH_KEY); },
  set(a: string, r: string) { localStorage.setItem(ACCESS_KEY, a); localStorage.setItem(REFRESH_KEY, r); },
  clear() { localStorage.removeItem(ACCESS_KEY); localStorage.removeItem(REFRESH_KEY); },
};

let onSessionExpired: () => void = () => {};
export function setSessionExpiredHandler(fn: () => void) { onSessionExpired = fn; }

async function toError(res: Response): Promise<ApiError> {
  let body: any = null;
  try { body = await res.json(); } catch { /* not JSON */ }
  const fieldErrors: Record<string, string> = {};
  const raw = body?.errors ?? body?.fieldErrors;
  if (Array.isArray(raw)) raw.forEach((e: any) => { if (e?.field) fieldErrors[e.field] = e.message; });
  else if (raw && typeof raw === "object") Object.assign(fieldErrors, raw);
  const detail = body?.detail || body?.title || `Request failed (${res.status})`;
  return new ApiError(res.status, body?.code ?? "ERROR", detail, fieldErrors);
}

let refreshing: Promise<boolean> | null = null;

/** Exchanges the refresh token for a new pair. Concurrent callers share one request (tokens rotate on use). */
function refreshTokens(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const refresh = tokens.refresh;
      if (!refresh) return false;
      try {
        const res = await fetch(`${BASE}/api/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: refresh }),
        });
        if (!res.ok) return false;
        const data = (await res.json()) as AuthResponse;
        tokens.set(data.accessToken, data.refreshToken);
        return true;
      } catch {
        return false;
      } finally {
        setTimeout(() => { refreshing = null; }, 0);
      }
    })();
  }
  return refreshing;
}

async function raw(path: string, init: RequestInit = {}, auth = true, retry = true): Promise<Response> {
  const headers = new Headers(init.headers);
  if (auth && tokens.access) headers.set("Authorization", `Bearer ${tokens.access}`);
  const res = await fetch(`${BASE}${path}`, { ...init, headers });
  if (res.status === 401 && auth && retry) {
    const hadSession = Boolean(tokens.access || tokens.refresh);
    if (hadSession && (await refreshTokens())) return raw(path, init, auth, false);
    if (hadSession) {
      tokens.clear();
      onSessionExpired();
    }
  }
  return res;
}

export async function request<T>(method: string, path: string, body?: unknown, auth = true): Promise<T> {
  const init: RequestInit = { method };
  if (body instanceof FormData) init.body = body;
  else if (body !== undefined) { init.body = JSON.stringify(body); init.headers = { "Content-Type": "application/json" }; }
  const res = await raw(path, init, auth);
  if (!res.ok) throw await toError(res);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  del: <T>(path: string) => request<T>("DELETE", path),
  upload: <T>(path: string, form: FormData) => request<T>("POST", path, form),
  public: <T>(method: string, path: string, body?: unknown) => request<T>(method, path, body, false),
};

/** Fetches a protected file and saves it through the browser. */
export async function download(path: string, fallbackName: string): Promise<void> {
  const res = await raw(path);
  if (!res.ok) throw await toError(res);
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : fallbackName;
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

/** POSTs a chat message and reads the SSE reply. Calls onToken for each chunk; resolves when "done" arrives. */
export async function streamChat(
  sessionId: number, content: string, onToken: (t: string) => void, signal?: AbortSignal,
): Promise<void> {
  const res = await raw(`/api/chat/sessions/${sessionId}/messages/stream`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
    body: JSON.stringify({ content }),
    signal,
  });
  if (!res.ok) throw await toError(res);
  if (!res.body) throw new ApiError(0, "NO_STREAM", "Streaming is not supported by this browser");
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let event = "message";
  let data: string[] = [];
  const dispatch = () => {
    if (data.length === 0) { event = "message"; return; }
    const payload = data.join("\n");
    if (event === "token") onToken(payload);
    else if (event === "error") {
      let msg = payload;
      try { msg = JSON.parse(payload).error ?? payload; } catch { /* keep raw */ }
      throw new ApiError(503, "AI_UNAVAILABLE", msg);
    }
    data = []; event = "message";
  };
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, idx).replace(/\r$/, "");
      buffer = buffer.slice(idx + 1);
      if (line === "") dispatch();
      else if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5)); // keep leading space: it belongs to the token
    }
  }
  dispatch();
}
