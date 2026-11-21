import { useEffect, useState, type ReactNode } from 'react'
import { getToken, resetSession, validateSession, markSessionStale } from '../utils/identity'

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
    // Validate the stored session against the server on first mount,
    // and re-validate periodically to maintain consistency.
    let mounted = true
    let revalidationTimeout: ReturnType<typeof setTimeout>

    const validateAndSet = async () => {
      if (!mounted) return
      setChecking(true)
      const isValid = await validateSession()
      if (mounted) {
        setChecking(false)
        if (!isValid && typeof window !== 'undefined' && window.location.pathname !== '/login') {
          window.location.assign('/login')
        }
      }
    }

    // Run on mount
    validateAndSet()

    // Re-validate periodically (every 2 minutes) and on visibility change
    const revalidate = () => {
      if (!mounted) return
      revalidationTimeout = setTimeout(validateAndSet, 120_000)
    }
    revalidate()
    const visibilityChange = () => {
      if (document.visibilityState === 'visible') revalidate()
    }
    document.addEventListener('visibilitychange', visibilityChange)

    return () => {
      mounted = false
      clearTimeout(revalidationTimeout)
      document.removeEventListener('visibilitychange', visibilityChange)
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

/** Call this after sign-in to mark the session stale and trigger re-validation. */
export function triggerRevalidationAfterSignIn(): void {
  markSessionStale()
}

/** Sign out from anywhere: clears every user-scoped cache, then leaves. */
export function signOut(): void {
  resetSession('logout')
}
