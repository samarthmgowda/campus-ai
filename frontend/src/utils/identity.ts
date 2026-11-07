/**
 * Session identity — the acting user for this session.
 *
 * The backend authenticates with a JWT: the token is obtained from
 * POST /auth/login and sent on every request as `Authorization: Bearer <token>`.
 * We keep the token plus a small, display-only profile snapshot in
 * localStorage so the shell can render a name and route by role without a
 * round-trip on first paint.
 *
 * The snapshot is NOT a security boundary — the server trusts only the token,
 * and every role check that matters is re-enforced there.
 */

export type Role = 'student' | 'alumni' | 'recruiter' | 'admin'

const TOKEN_KEY = 'authToken'
const ID_KEY = 'authUserId'
const ROLE_KEY = 'authUserRole'
const NAME_KEY = 'authUserName'
const PROFILE_KEY = 'authProfileId'

/** The raw JWT, or null when signed out. */
export function getToken(): string | null {
  const raw = localStorage.getItem(TOKEN_KEY)
  return raw && raw.trim() !== '' ? raw : null
}

/** The auth account id (User.id), or null. */
export function getId(): string | null {
  const raw = localStorage.getItem(ID_KEY)
  return raw && raw.trim() !== '' ? raw : null
}

export function getRole(): Role | null {
  const raw = localStorage.getItem(ROLE_KEY)
  if (raw === 'student' || raw === 'alumni' || raw === 'recruiter' || raw === 'admin') return raw
  return null
}

export function getName(): string | null {
  const raw = localStorage.getItem(NAME_KEY)
  return raw && raw.trim() !== '' ? raw : null
}

/** The profile id for the acting role, when the account is bound to one. */
export function getProfileId(): string | null {
  const raw = localStorage.getItem(PROFILE_KEY)
  return raw && raw.trim() !== '' ? raw : null
}

export function isSignedIn(): boolean {
  return getToken() !== null
}

/** Persist a successful login: the token plus the display/route snapshot. */
export function setSession(token: string, account: { id: number | string; role: Role; name?: string; profileId?: number | null }): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(ID_KEY, String(account.id))
  localStorage.setItem(ROLE_KEY, account.role)
  if (account.name) localStorage.setItem(NAME_KEY, account.name)
  else localStorage.removeItem(NAME_KEY)
  if (account.profileId != null) localStorage.setItem(PROFILE_KEY, String(account.profileId))
  else localStorage.removeItem(PROFILE_KEY)
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ID_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem(NAME_KEY)
  localStorage.removeItem(PROFILE_KEY)
}

export const ROLE_LABELS: Record<Role, string> = {
  student: 'Student',
  alumni: 'Alumni',
  recruiter: 'Recruiter',
  admin: 'Admin',
}
