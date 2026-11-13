import { getToken, resetSession } from '../utils/identity'

const API_BASE = ''

/**
 * Broadcast when the API stops (and starts) answering.
 *
 * A dead server otherwise surfaces as one red message per panel that happened
 * to be fetching, which reads as "everything is broken in many ways". These
 * events let a single component take over the screen with one explanation.
 * They are only fired for status 0 — a real HTTP response, even a 500, means
 * the server is up and the page can report its own error.
 */
export const CONNECTION_LOST_EVENT = 'campus:connection-lost'
export const CONNECTION_RESTORED_EVENT = 'campus:connection-restored'

let connectionDown = false

function announceConnectionLost(): void {
  connectionDown = true
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(CONNECTION_LOST_EVENT))
}

function announceConnectionRestored(): void {
  // Only the transition matters — otherwise every successful request would
  // dispatch, and a page making twenty calls would fire twenty events.
  if (!connectionDown) return
  connectionDown = false
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(CONNECTION_RESTORED_EVENT))
}

/** Thrown with the HTTP status attached, so callers can special-case 401/403. */
export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }

  /** The caller is not signed in (or the token is dead). Sign in again. */
  get isAuthError(): boolean {
    return this.status === 401
  }

  /** The caller is signed in, but this account may not do this. */
  get isForbidden(): boolean {
    return this.status === 403
  }

  /** No response at all — server down, offline, DNS. */
  get isNetworkError(): boolean {
    return this.status === 0
  }
}

function authHeader(): Record<string, string> {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * One recoverable "your session ended" event per page load.
 *
 * Several queries reject with 401 at the same moment when a token dies, and
 * each one must not fight to redirect. The session is torn down once; every
 * subsequent 401 just rejects with the same clear error. The full reload is
 * deliberate: it drops all in-memory state, so the next sign-in starts from a
 * genuinely clean slate instead of inheriting the previous account's caches.
 */
let expiredHandled = false

function handleUnauthorized(message?: string): never {
  const detail = message || 'Your session has ended. Please sign in again.'
  if (!expiredHandled) {
    expiredHandled = true
    resetSession('expired')
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.assign('/login')
    }
  }
  throw new ApiError(401, detail)
}

/** Pull the most useful human sentence out of an error body. */
async function errorMessage(res: Response): Promise<string> {
  const body = await res.json().catch(() => null)
  if (body && typeof body === 'object') {
    const b = body as Record<string, unknown>
    for (const key of ['error', 'message', 'detail']) {
      const value = b[key]
      if (typeof value === 'string' && value.trim()) return value
    }
  }
  if (res.status === 403) return 'Your account does not have access to this.'
  if (res.status === 404) return 'That item could not be found.'
  if (res.status >= 500) return 'The server ran into a problem. Please try again.'
  return `Request failed (HTTP ${res.status}).`
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...authHeader(),
    ...(options.headers as Record<string, string>),
  }

  let res: Response
  try {
    res = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
      credentials: 'omit',
    })
  } catch {
    announceConnectionLost()
    throw new ApiError(0, 'Could not reach the campus server. Check your connection and try again.')
  }

  // Any answer at all means the server is alive again.
  announceConnectionRestored()

  // 401 means "we do not know who you are" — the session is dead, so drop it.
  // 403 means "we know exactly who you are, and this is not for you" — the
  // session is fine and signing in again would change nothing, so it is a
  // plain rejection the page renders itself.
  if (res.status === 401) return handleUnauthorized(await errorMessage(res))

  if (!res.ok) {
    throw new ApiError(res.status, await errorMessage(res))
  }

  if (res.status === 204) return undefined as T
  return res.json().catch(() => {
    throw new Error(`HTTP ${res.status}: Response was not valid JSON`)
  })
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  postForm: <T>(path: string, formData: FormData) =>
    fetch(path, {
      method: 'POST',
      body: formData,
      headers: authHeader(), // Content-Type set by the browser for FormData
      credentials: 'omit',
    })
      .catch(() => {
        throw new ApiError(0, 'Could not reach the campus server. Check your connection and try again.')
      })
      .then(async (res) => {
        if (res.status === 401) return handleUnauthorized(await errorMessage(res))
        if (!res.ok) {
          throw new ApiError(res.status, await errorMessage(res))
        }
        return res.json() as Promise<T>
      }),
}
