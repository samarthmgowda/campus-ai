import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { isSignedIn, getRole, type Role } from '../utils/identity'

/**
 * Wraps every authenticated route. Sends visitors who have not picked a profile
 * to the sign-in page, remembering where they were headed.
 */
export function RequireAuth() {
  const location = useLocation()
  if (!isSignedIn()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/**
 * Sends a visitor to the landing page if they are already signed in — used by
 * the login route so a returning user does not have to re-pick a profile.
 */
export function RedirectIfSignedIn() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  if (isSignedIn()) return <Navigate to={from ?? '/home'} replace />
  return <Outlet />
}

/**
 * Restricts a route to specific roles.
 *
 * Not a security boundary — the backend is the authority on what a role may
 * do. This exists so a student who follows an alumni-only link gets a clear
 * destination rather than a screen full of 403s.
 */
export function RequireRole({ allow }: { allow: Role[] }) {
  const role = getRole() ?? 'student'
  const home: Record<Role, string> = {
    student: '/dashboard',
    alumni: '/dashboard/alumni',
    recruiter: '/dashboard/recruiter',
    admin: '/admin',
  }
  if (!allow.includes(role)) {
    return <Navigate to={home[role]} replace />
  }
  return <Outlet />
}
