import { useEffect, useState, type ReactNode } from 'react'
import { api, ApiError } from '../api/client'
import { getToken, setSession, refreshSession, resetSession } from '../utils/identity'

/**
 * Validates the stored session against the server before the app renders.
 *
 * The token and profile snapshot live in localStorage, so a browser can be
 * holding a session for an account that no longer exists — a wiped dev
 * database, a deleted user, a token signed with a rotated secret. Without this
 * check the app renders instantly and then every page fails at once, which
 * looks like "the whole site is broken" rather than "sign in again".
 *
 * One GET /auth/me on boot turns that into a clean redirect:
 *   - 401  → the session is dead; drop it and go to the login screen.
 *   - 200  → adopt the server's view of the account (role, name, profile id),
 *            so the snapshot can never drift from the token.
 *   - network error → proceed with the stored snapshot. Being offline is not
 *            the same as being signed out, and each page shows its own error.
 */
export default function SessionBootstrap({ children }: { children: ReactNode }) {
  const [checking, setChecking] = useState(() => getToken() !== null)

  useEffect(() => {
    const token = getToken()
    if (!token) {
      setChecking(false)
      return
    }

    let cancelled = false
    // A slow or hung boot check must not hold the app hostage; fall through to
    // the stored snapshot and let individual requests report their own errors.
    const timeout = window.setTimeout(() => {
      if (!cancelled) setChecking(false)
    }, 6000)

    type Me = { id: number; email: string; fullName: string; role: string; profileId: number | null }

    api
      .get<Me>('/auth/me')
      .then((me) => {
        if (cancelled) return
        setSession(token, {
          id: me.id,
          role: me.role as never,
          name: me.fullName,
          profileId: me.profileId,
        })
      })
      .catch((err) => {
        if (cancelled) return
        if (err instanceof ApiError && err.isAuthError) {
          // api/client already tore the session down and redirected.
          return
        }
        // Offline or server error: keep the snapshot, let pages surface it.
        refreshSession()
      })
      .finally(() => {
        if (cancelled) return
        window.clearTimeout(timeout)
        setChecking(false)
      })

    return () => {
      cancelled = true
      window.clearTimeout(timeout)
    }
  }, [])

  // Only a boot with an existing token shows the splash, and only briefly.
  if (checking) {
    return (
      <div
        className="flex min-h-dvh items-center justify-center bg-er-950"
        role="status"
        aria-live="polite"
      >
        <div className="flex flex-col items-center gap-3">
          <span
            className="h-9 w-9 animate-spin rounded-full border-2 border-er-700 border-t-er-300"
            aria-hidden="true"
          />
          <p className="text-body-sm text-ink-400">Restoring your session…</p>
        </div>
      </div>
    )
  }

  return <>{children}</>
}

/** Sign out from anywhere: clears every user-scoped cache, then leaves. */
export function signOut(): void {
  resetSession('logout')
}
