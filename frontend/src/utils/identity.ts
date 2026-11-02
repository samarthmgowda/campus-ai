/**
 * Demo identity — the acting user for this session.
 *
 * Held in localStorage as two keys (id + role) and sent to the backend as a
 * single composite `X-Demo-User` header ("role:id"), because Student, Alumni
 * and Recruiter rows live in separate tables with independent ID sequences.
 */

export type Role = 'student' | 'alumni' | 'recruiter'

const ID_KEY = 'demoUserId'
const ROLE_KEY = 'demoUserRole'
const NAME_KEY = 'demoUserName'

export function getRole(): Role | null {
  const raw = localStorage.getItem(ROLE_KEY)
  if (raw === 'student' || raw === 'alumni' || raw === 'recruiter') return raw
  return null
}

export function getId(): string | null {
  const raw = localStorage.getItem(ID_KEY)
  return raw && raw.trim() !== '' ? raw : null
}

export function getName(): string | null {
  const raw = localStorage.getItem(NAME_KEY)
  return raw && raw.trim() !== '' ? raw : null
}

export function isSignedIn(): boolean {
  return getId() !== null
}

export function setIdentity(id: string | number, role: Role, name?: string): void {
  localStorage.setItem(ID_KEY, String(id))
  localStorage.setItem(ROLE_KEY, role)
  if (name) localStorage.setItem(NAME_KEY, name)
  else localStorage.removeItem(NAME_KEY)
}

export function clearIdentity(): void {
  localStorage.removeItem(ID_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem(NAME_KEY)
}

/**
 * The composite header value, e.g. "student:1".
 * Falls back to a student role when only an id is stored, matching the
 * backend's tolerant parsing.
 */
export function authHeaderValue(): string | null {
  const id = getId()
  if (id === null) return null
  return `${getRole() ?? 'student'}:${id}`
}

export const ROLE_LABELS: Record<Role, string> = {
  student: 'Student',
  alumni: 'Alumni',
  recruiter: 'Recruiter',
}
