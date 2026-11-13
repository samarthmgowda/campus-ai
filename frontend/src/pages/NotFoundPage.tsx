import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useReducedMotion } from '../hooks/useReducedMotion'
import { cn } from '../utils/cn'
import { navItemsForRole } from '../config/navigation'
import { useSession } from '../utils/identity'
import { Compass, ArrowLeft, ArrowRight, Home, Sparkles, type LucideIcon } from 'lucide-react'

/**
 * 404. Mirrors the landing page's structure so a wrong turn still feels like
 * part of the product, and offers real destinations rather than only an apology.
 *
 * Colour follows the same 6:3:1 discipline as the landing page: midnight black
 * base, electric blue for every accent, and sunset orange reserved solely for
 * the one primary button.
 */
export default function NotFoundPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const reduced = useReducedMotion()
  const session = useSession()
  const signedIn = session !== null
  const role = session?.role ?? null

  const destinations = signedIn
    ? navItemsForRole(role)
        .filter((i) => ['/home', '/chat', '/search', '/community'].includes(i.path))
        .slice(0, 4)
    : []

  return (
    <div className="relative flex min-h-dvh items-center justify-center overflow-hidden bg-er-950 px-5 py-16">
      {/* Blue only. Orange never appears as atmosphere — it means "press this". */}
      <div className="pointer-events-none absolute inset-0" aria-hidden="true">
        <div
          className={cn(
            'absolute -top-40 right-[-10%] h-[460px] w-[460px] rounded-full opacity-[0.18] blur-[90px]',
            'bg-[radial-gradient(circle_at_center,#2F6BFF,transparent_70%)]',
            !reduced && 'animate-drift'
          )}
        />
        <div
          className={cn(
            'absolute -bottom-48 left-[-12%] h-[420px] w-[420px] rounded-full opacity-[0.12] blur-[90px]',
            'bg-[radial-gradient(circle_at_center,#6385FF,transparent_70%)]',
            !reduced && 'animate-drift'
          )}
          style={!reduced ? { animationDelay: '-7s' } : undefined}
        />
      </div>

      <div className={cn('relative w-full max-w-xl', !reduced && 'animate-rise')}>
        <span className="inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-er-700/50 bg-er-900">
          <Compass className="h-6 w-6 text-ink-300" aria-hidden="true" />
        </span>

        <p className="mt-6 font-mono text-body-sm font-medium text-ink-400">404</p>
        <h1 className="mt-2 font-display font-bold tracking-[-0.02em] text-[clamp(2rem,5vw,2.75rem)] leading-[1.08] text-ink-50">
          That page is not on campus.
        </h1>
        <p className="mt-4 max-w-[52ch] text-body leading-relaxed text-ink-300">
          We could not find anything at{' '}
          <code className="rounded bg-er-900 px-1.5 py-0.5 font-mono text-body-sm text-ink-200 break-all">
            {location.pathname}
          </code>
          . The link may be old, or the page may have moved.
        </p>

        <div className="mt-8 flex flex-wrap items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-2 rounded-xl border border-er-700/50 bg-er-900 px-5 py-2.5 text-body-sm font-semibold text-ink-100 transition-colors hover:border-er-500/60 hover:bg-er-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Go back
          </button>
          <Link
            to={signedIn ? '/home' : '/'}
            className="inline-flex items-center gap-2 rounded-xl bg-er-orange px-5 py-2.5 text-body-sm font-semibold text-er-950 shadow-[0_4px_20px_rgba(255,122,26,0.28)] transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-orange focus-visible:ring-offset-2 focus-visible:ring-offset-er-950"
          >
            <Home className="h-4 w-4" aria-hidden="true" />
            {signedIn ? 'Back to home' : 'Back to start'}
          </Link>
        </div>

        {destinations.length > 0 && (
          <nav className="mt-10 border-t border-er-800/70 pt-6" aria-label="Suggested pages">
            <p className="text-micro font-semibold uppercase tracking-wide text-ink-400">
              Try one of these instead
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              {destinations.map(
                (d: { path: string; label: string; icon: LucideIcon; hint?: string }) => (
                  <Link
                    key={d.path}
                    to={d.path}
                    className="group flex items-center gap-3 rounded-xl border border-er-800 bg-er-900 px-3.5 py-3 transition-colors hover:border-er-600 hover:bg-er-800/60"
                  >
                    <d.icon
                      className="h-[18px] w-[18px] flex-shrink-0 text-ink-300"
                      aria-hidden="true"
                    />
                    <span className="min-w-0">
                      <span className="block text-body-sm font-medium text-ink-100">{d.label}</span>
                      {d.hint && (
                        <span className="block truncate text-micro text-ink-400">{d.hint}</span>
                      )}
                    </span>
                    <ArrowRight
                      className="ml-auto h-4 w-4 flex-shrink-0 text-ink-400 transition-transform group-hover:translate-x-0.5 group-hover:text-ink-100"
                      aria-hidden="true"
                    />
                  </Link>
                )
              )}
            </div>
          </nav>
        )}

        {!signedIn && (
          <p className="mt-8 flex items-center gap-2 text-body-sm text-ink-400">
            <Sparkles className="h-4 w-4 text-ink-400" aria-hidden="true" />
            <span>
              Not signed in yet?{' '}
              <Link
                to="/login"
                className="font-semibold text-ink-200 underline underline-offset-2 hover:text-ink-50"
              >
                Sign in
              </Link>
            </span>
          </p>
        )}
      </div>
    </div>
  )
}
