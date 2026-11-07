import { getToken, clearSession } from '../utils/identity'

const API_BASE = ''

/** Thrown with the HTTP status attached, so callers can special-case 401/403. */
export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function authHeader(): Record<string, string> {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function handleUnauthorized(): Promise<never> {
  // An expired or revoked token is unrecoverable in-place — drop the session
  // and send the user back to sign in.
  clearSession()
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
  throw new ApiError(401, 'Session expired — please sign in again')
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...authHeader(),
    ...(options.headers as Record<string, string>),
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    credentials: 'omit',
  })

  if (res.status === 401) return handleUnauthorized()

  if (!res.ok) {
    const error = await res.json().catch(() => ({ error: `HTTP ${res.status}` }))
    throw new ApiError(res.status, error.error || error.message || `HTTP ${res.status}`)
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
    }).then(async (res) => {
      if (res.status === 401) return handleUnauthorized()
      if (!res.ok) {
        const error = await res.json().catch(() => ({ error: 'Request failed' }))
        throw new ApiError(res.status, error.error || `HTTP ${res.status}`)
      }
      return res.json() as Promise<T>
    }),
}
