import { Navigate, Outlet, Link, useLocation } from 'react-router-dom'
import { useSession, ROLE_LABELS, type Role } from '../utils/identity'
import { ShieldAlert, ArrowLeft } from 'lucide-react'

/**
 * Wraps every authenticated route. Sends visitors who have not signed in to
 * the sign-in page, remembering where they were headed.
 *
 * Reads the reactive session, so the moment a token is dropped — by a 401, by
 * sign-out in another tab, by the boot check — the guard re-renders and the
 * protected tree goes away instead of rendering against a dead identity.
 */
export function RequireAuth() {
  const location = useLocation()
  const session = useSession()
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/**
 * The login route. If a valid session already exists, skip the form.
 *
 * This is why "Enter Campus" appeared to log straight in: the browser still
 * held the previous token, and this guard correctly honoured it. That is now
 * explicit rather than silent — the app validates the token on boot, so a
 * stale one is discarded before it can be honoured, and the login page shows
 * who is signed in with a clear "continue as" / "use another account" choice
 * (see LoginPage) instead of an unexplained jump.
 */
export function RedirectIfSignedIn() {
  const location = useLocation()
  const session = useSession()
  const from = (location.state as { from?: string } | null)?.from
  if (session) return <Navigate to={from ?? '/home'} replace />
  return <Outlet />
}

/**
 * Restricts a route to specific roles.
 *
 * Not a security boundary — the backend is the authority on what a role may
 * do, and it answers 403 on its own. This exists so a student who follows an
 * alumni-only link is told why, rather than being silently teleported to a
 * different page with no explanation and left to wonder what happened.
 */
export function RequireRole({ allow }: { allow: Role[] }) {
  const session = useSession()
  const location = useLocation()
  const role = session?.role ?? 'student'

  if (allow.includes(role)) return <Outlet />

  return <RoleMismatch role={role} allow={allow} path={location.pathname} />
}

function RoleMismatch({ role, allow, path }: { role: Role; allow: Role[]; path: string }) {
  const allowed = allow.map((r) => ROLE_LABELS[r]).join(' or ')
  return (
    <div className="mx-auto max-w-lg py-10">
      <div className="rounded-2xl border border-er-800 bg-er-900 p-6">
        <span className="inline-flex h-11 w-11 items-center justify-center rounded-xl border border-er-700/60 bg-er-800">
          <ShieldAlert className="h-5 w-5 text-ink-200" aria-hidden="true" />
        </span>
        <h1 className="mt-4 font-display text-heading-lg font-semibold text-ink-50">
          This page is for {allowed} accounts
        </h1>
        <p className="mt-2 text-body-sm leading-relaxed text-ink-300">
          You are signed in as{' '}
          <span className="font-semibold text-ink-100">{ROLE_LABELS[role]}</span>, so{' '}
          <code className="rounded bg-er-950 px-1.5 py-0.5 font-mono text-micro text-ink-200">
            {path}
          </code>{' '}
          is not available to this account. Nothing is broken — this is a permission
          boundary, not an error.
        </p>
        <div className="mt-5 flex flex-wrap gap-2">
          <Link
            to="/home"
            className="inline-flex items-center gap-2 rounded-xl bg-er-orange px-4 py-2.5 text-body-sm font-semibold text-er-950 transition-transform hover:-translate-y-[1px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-orange focus-visible:ring-offset-2 focus-visible:ring-offset-er-900"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Back to home
          </Link>
          <Link
            to="/settings"
            className="inline-flex items-center gap-2 rounded-xl border border-er-700/60 bg-er-800 px-4 py-2.5 text-body-sm font-semibold text-ink-100 transition-colors hover:border-er-500/60"
          >
            Switch account
          </Link>
        </div>
      </div>
    </div>
  )
}
