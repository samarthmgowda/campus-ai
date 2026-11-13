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
 *
 * Two things matter for correctness:
 *
 *  1. **It is observable.** Reading localStorage in a component body is a
 *     one-shot snapshot: it is captured at render and never re-read, so a
 *     component that mounted as one user kept rendering that user after a
 *     switch. Every reader now goes through {@link useSession}, which
 *     subscribes to the store below and re-renders on change.
 *
 *  2. **Every sign-in and sign-out is a hard boundary.** Caches, in-flight
 *     state and the conversation belong to the account that created them, so
 *     both transitions run {@link resetSession} first — that is what stops one
 *     account's data from being served to the next.
 */

import { useSyncExternalStore } from 'react'

export type Role = 'student' | 'alumni' | 'recruiter' | 'admin'

const TOKEN_KEY = 'authToken'
const ID_KEY = 'authUserId'
const ROLE_KEY = 'authUserRole'
const NAME_KEY = 'authUserName'
const PROFILE_KEY = 'authProfileId'

/** The display/route snapshot, as one immutable value. */
export interface Session {
  userId: string
  role: Role
  name: string | null
  profileId: string | null
}

function readSession(): Session | null {
  if (typeof localStorage === 'undefined') return null
  if (!localStorage.getItem(TOKEN_KEY)) return null
  const id = localStorage.getItem(ID_KEY)
  if (!id) return null

  const rawRole = localStorage.getItem(ROLE_KEY)
  const role: Role =
    rawRole === 'student' || rawRole === 'alumni' || rawRole === 'recruiter' || rawRole === 'admin'
      ? rawRole
      : 'student'

  const name = localStorage.getItem(NAME_KEY)
  const profileId = localStorage.getItem(PROFILE_KEY)
  return {
    userId: id,
    role,
    name: name && name.trim() !== '' ? name : null,
    profileId: profileId && profileId.trim() !== '' ? profileId : null,
  }
}

/** Stable identity for `useSyncExternalStore` — the same object until it changes. */
let snapshot: Session | null = readSession()
const listeners = new Set<() => void>()

function notify(): void {
  snapshot = readSession()
  listeners.forEach((l) => l())
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Re-read the store. Used after a write it did not perform itself. */
export function refreshSession(): void {
  notify()
}

/* ------------------------------------------------------------------ */
/* Reset hooks                                                         */
/* ------------------------------------------------------------------ */

/**
 * Anything that holds user-scoped state registers here. The QueryClient cache
 * and the chat provider both do; that way "signed out" and "signed in as
 * somebody else" are honoured in one place rather than at each call site.
 */
type ResetHandler = (reason: 'logout' | 'login' | 'expired') => void
const resetHandlers = new Set<ResetHandler>()

export function onSessionReset(handler: ResetHandler): () => void {
  resetHandlers.add(handler)
  return () => {
    resetHandlers.delete(handler)
  }
}

/**
 * Tear down everything that belonged to the previous account.
 *
 * Called *before* every login and on every logout, so no cached response,
 * pending conversation or stale name can survive the boundary. Handlers run
 * first, while the old identity is still readable — a handler that needs to
 * know whose data it is dropping can still ask.
 */
export function resetSession(reason: 'logout' | 'login' | 'expired' = 'logout'): void {
  resetHandlers.forEach((h) => {
    try {
      h(reason)
    } catch {
      // One broken listener must not strand the user in a half-cleared session.
    }
  })
  clearSession()
}

/* ------------------------------------------------------------------ */
/* Reads                                                               */
/* ------------------------------------------------------------------ */

/** The raw JWT, or null when signed out. */
export function getToken(): string | null {
  if (typeof localStorage === 'undefined') return null
  const raw = localStorage.getItem(TOKEN_KEY)
  return raw && raw.trim() !== '' ? raw : null
}

/** The auth account id (User.id), or null. */
export function getId(): string | null {
  return snapshot?.userId ?? null
}

export function getRole(): Role | null {
  return snapshot?.role ?? null
}

export function getName(): string | null {
  return snapshot?.name ?? null
}

/** The profile id for the acting role, when the account is bound to one. */
export function getProfileId(): string | null {
  return snapshot?.profileId ?? null
}

export function isSignedIn(): boolean {
  return getToken() !== null && snapshot !== null
}

/** The current snapshot, or null. */
export function getSession(): Session | null {
  return snapshot
}

/**
 * Reactive session. Re-renders the calling component whenever the signed-in
 * account changes — use this instead of calling the getters during render.
 */
export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, getSession, () => null)
}

/* ------------------------------------------------------------------ */
/* Writes                                                              */
/* ------------------------------------------------------------------ */

/** Persist a successful login: the token plus the display/route snapshot. */
export function setSession(
  token: string,
  account: { id: number | string; role: Role; name?: string; profileId?: number | null },
): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(ID_KEY, String(account.id))
  localStorage.setItem(ROLE_KEY, account.role)
  if (account.name) localStorage.setItem(NAME_KEY, account.name)
  else localStorage.removeItem(NAME_KEY)
  if (account.profileId != null) localStorage.setItem(PROFILE_KEY, String(account.profileId))
  else localStorage.removeItem(PROFILE_KEY)
  notify()
}

/** Drop the stored token and snapshot. Does not notify reset handlers. */
export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ID_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem(NAME_KEY)
  localStorage.removeItem(PROFILE_KEY)
  notify()
}

export const ROLE_LABELS: Record<Role, string> = {
  student: 'Student',
  alumni: 'Alumni',
  recruiter: 'Recruiter',
  admin: 'Admin',
}

/** Where a role lands after signing in. */
export const ROLE_HOME: Record<Role, string> = {
  student: '/home',
  alumni: '/home',
  recruiter: '/home',
  admin: '/home',
}
