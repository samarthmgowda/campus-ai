import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useReducedMotion } from '../hooks/useReducedMotion'
import { cn } from '../utils/cn'
import { navItemsForRole } from '../config/navigation'
import { getRole, isSignedIn } from '../utils/identity'
import { Compass, ArrowLeft, ArrowRight, Home, Sparkles, type LucideIcon } from 'lucide-react'

/**
 * 404. Mirrors the landing page's structure so a wrong turn still feels like
 * part of the product, and offers real destinations rather than only an apology.
 */
export default function NotFoundPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const reduced = useReducedMotion()
  const signedIn = isSignedIn()
  const role = getRole()

  const destinations = signedIn
    ? navItemsForRole(role)
        .filter((i) => ['/home', '/chat', '/search', '/community'].includes(i.path))
        .slice(0, 4)
    : []

  return (
    <div className="relative flex min-h-dvh items-center justify-center overflow-hidden bg-[#f5f7fa] px-5 py-16">
      <div className="pointer-events-none absolute inset-0" aria-hidden="true">
        <div
          className={cn(
            'absolute -top-40 right-[-10%] h-[460px] w-[460px] rounded-full opacity-[0.20] blur-[90px]',
            'bg-[radial-gradient(circle_at_center,#667eea,transparent_70%)]',
            !reduced && 'animate-drift'
          )}
        />
        <div
          className={cn(
            'absolute -bottom-48 left-[-12%] h-[420px] w-[420px] rounded-full opacity-[0.16] blur-[90px]',
            'bg-[radial-gradient(circle_at_center,#764ba2,transparent_70%)]',
            !reduced && 'animate-drift'
          )}
          style={!reduced ? { animationDelay: '-7s' } : undefined}
        />
      </div>

      <div className={cn('relative w-full max-w-xl', !reduced && 'animate-rise')}>
        <span className="inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-campus-100 bg-white shadow-card">
          <Compass className="h-6 w-6 text-campus-600" aria-hidden="true" />
        </span>

        <p className="mt-6 font-mono text-body-sm font-medium text-campus-500">404</p>
        <h1 className="mt-2 font-display font-bold tracking-[-0.02em] text-[clamp(2rem,5vw,2.75rem)] leading-[1.08] text-campus-950">
          That page is not on campus.
        </h1>
        <p className="mt-4 max-w-[52ch] text-body leading-relaxed text-surface-600">
          We could not find anything at{' '}
          <code className="rounded bg-white px-1.5 py-0.5 font-mono text-body-sm text-surface-700 break-all">
            {location.pathname}
          </code>
          . The link may be old, or the page may have moved.
        </p>

        <div className="mt-8 flex flex-wrap items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-2 rounded-xl border border-campus-900/15 bg-white px-5 py-2.5 text-body-sm font-semibold text-campus-900 transition-colors hover:border-campus-900/30 hover:bg-campus-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Go back
          </button>
          <Link
            to={signedIn ? '/home' : '/'}
            className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] px-5 py-2.5 text-body-sm font-semibold text-white shadow-card transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500 focus-visible:ring-offset-2"
          >
            <Home className="h-4 w-4" aria-hidden="true" />
            {signedIn ? 'Back to home' : 'Back to start'}
          </Link>
        </div>

        {destinations.length > 0 && (
          <nav className="mt-10 border-t border-campus-900/[0.08] pt-6" aria-label="Suggested pages">
            <p className="text-micro font-semibold uppercase tracking-wide text-surface-400">
              Try one of these instead
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              {destinations.map((d: { path: string; label: string; icon: LucideIcon; hint?: string }) => (
                <Link
                  key={d.path}
                  to={d.path}
                  className="group flex items-center gap-3 rounded-xl border border-campus-900/[0.08] bg-white px-3.5 py-3 transition-colors hover:border-campus-300 hover:bg-campus-50/50"
                >
                  <d.icon className="h-[18px] w-[18px] flex-shrink-0 text-campus-500" aria-hidden="true" />
                  <span className="min-w-0">
                    <span className="block text-body-sm font-medium text-campus-950">{d.label}</span>
                    {d.hint && <span className="block text-micro text-surface-400 truncate">{d.hint}</span>}
                  </span>
                  <ArrowRight
                    className="ml-auto h-4 w-4 flex-shrink-0 text-surface-300 transition-transform group-hover:translate-x-0.5 group-hover:text-campus-500"
                    aria-hidden="true"
                  />
                </Link>
              ))}
            </div>
          </nav>
        )}

        {!signedIn && (
          <p className="mt-8 flex items-center gap-2 text-body-sm text-surface-500">
            <Sparkles className="h-4 w-4 text-accent-500" aria-hidden="true" />
            <span>
              Not signed in yet?{' '}
              <Link to="/login" className="font-semibold text-campus-600 underline underline-offset-2 hover:text-campus-700">
                Pick a demo profile
              </Link>
            </span>
          </p>
        )}
      </div>
    </div>
  )
}
