import { useState, useEffect } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { resetSession, setSession, useSession, ROLE_LABELS, type Role } from '../utils/identity'
import {
  ArrowLeft,
  ArrowRight,
  AlertTriangle,
  Loader2,
  Lock,
  Users,
  ShieldCheck,
  GraduationCap,
  Briefcase,
  Eye,
  EyeOff,
} from 'lucide-react'

/* ------------------------------------------------------------------ */
/* Colour: midnight black page, electric-blue accents, orange reserved */
/* for the single primary button. Same discipline as the landing page. */
/* ------------------------------------------------------------------ */

const CTA =
  'inline-flex w-full items-center justify-center gap-2 rounded-xl bg-er-orange px-6 py-3 text-body font-semibold text-er-950 shadow-[0_4px_20px_rgba(255,122,26,0.28)] transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-orange focus-visible:ring-offset-2 focus-visible:ring-offset-er-950 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0'

const FIELD =
  'w-full rounded-xl border border-er-700/60 bg-er-900 px-4 py-2.5 text-body-sm text-ink-50 placeholder:text-ink-500 transition-colors focus:border-er-500 focus:outline-none focus:ring-2 focus:ring-er-500/40'

const SIGNUP_ROLES: { value: Role; label: string; icon: typeof Users; hint: string }[] = [
  { value: 'student', label: 'Student', icon: GraduationCap, hint: 'Find teammates and projects' },
  { value: 'alumni', label: 'Alumni', icon: Users, hint: 'Mentor and refer' },
  { value: 'recruiter', label: 'Recruiter', icon: Briefcase, hint: 'Post roles and shortlist' },
]

type Session = {
  token: string
  id: number
  email: string
  fullName: string
  role: string
  profileId: number | null
}

export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const session = useSession()
  const from = (location.state as { from?: string } | null)?.from

  const [mode, setMode] = useState<'signin' | 'signup'>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState<Role>('student')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // The previous session, if one exists. This is the page that explains why
  // "Enter Campus" can look like it logs straight in: the browser still holds
  // a valid token. It is now a visible choice rather than a silent jump.
  const [previous, setPrevious] = useState(session)
  useEffect(() => {
    if (!loading) setPrevious(session)
  }, [session, loading])

  const handleAuth = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)

    // Clear the old account *before* asking for a new one. If the previous
    // user's caches survived into this login, the new account would render
    // the old account's data for the first 30 seconds — the exact reported
    // bug. Order matters: reset, then authenticate, then store.
    resetSession('login')

    try {
      const account = mode === 'signup'
        ? await api.post<Session>('/auth/signup', { email, password, fullName, role })
        : await api.post<Session>('/auth/login', { email, password })

      setSession(account.token, {
        id: account.id,
        role: account.role as Role,
        name: account.fullName,
        profileId: account.profileId,
      })
      navigate(from ?? '/home', { replace: true })
    } catch (e) {
      const message =
        e instanceof ApiError && e.isNetworkError
          ? e.message
          : e instanceof Error
            ? e.message
            : 'Could not sign in. Please try again.'
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  const switchAccount = () => {
    resetSession('logout')
    setPrevious(null)
    setMode('signin')
    setError(null)
    setEmail('')
    setPassword('')
  }

  return (
    <div className="grid min-h-dvh bg-er-950 lg:grid-cols-[1.05fr_1fr]">
      {/* ——— Left: the product, quietly ——— */}
      <aside className="relative hidden flex-col justify-between overflow-hidden border-r border-er-800/70 p-12 lg:flex">
        <div className="pointer-events-none absolute inset-0" aria-hidden="true">
          <div className="absolute -right-32 -top-40 h-[460px] w-[460px] rounded-full bg-[radial-gradient(circle_at_center,#2F6BFF,transparent_70%)] opacity-[0.22] blur-[90px]" />
          <div className="absolute -bottom-40 -left-24 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle_at_center,#6385FF,transparent_70%)] opacity-[0.14] blur-[90px]" />
        </div>

        <Link to="/" className="relative inline-flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl border border-er-700/60 bg-er-900">
            <Lock className="h-[18px] w-[18px] text-er-300" aria-hidden="true" />
          </span>
          <span className="font-display text-[17px] font-bold tracking-tight text-ink-50">
            Campus Connect
          </span>
        </Link>

        <div className="relative max-w-[46ch]">
          <p className="inline-flex items-center gap-1.5 rounded-full border border-er-700/60 bg-er-900 px-3 py-1 text-micro font-medium text-ink-300">
            <ShieldCheck className="h-3.5 w-3.5 text-er-300" aria-hidden="true" />
            Private to MIT Mysore
          </p>
          <h2 className="mt-5 font-display text-[clamp(1.9rem,3vw,2.6rem)] font-bold leading-[1.08] tracking-[-0.02em] text-ink-50">
            The campus network,
            <br />
            closed to everyone else.
          </h2>
          <p className="mt-4 text-body leading-relaxed text-ink-300">
            Find the people who are building what you want to build. Teams for hackathons,
            projects that ship, and the alumni who have already done it — all inside one
            verified campus network.
          </p>
        </div>

        <p className="relative text-caption text-ink-500">
          Every account is tied to a role. What you see is what your role can do.
        </p>
      </aside>

      {/* ——— Right: the form ——— */}
      <main className="flex items-center justify-center px-5 py-10 sm:px-10">
        <div className="w-full max-w-[420px]">
          <div className="mb-8 flex items-center justify-between lg:hidden">
            <Link to="/" className="inline-flex items-center gap-2.5">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl border border-er-700/60 bg-er-900">
                <Lock className="h-[18px] w-[18px] text-er-300" aria-hidden="true" />
              </span>
              <span className="font-display text-[17px] font-bold tracking-tight text-ink-50">
                Campus Connect
              </span>
            </Link>
          </div>

          <Link
            to="/"
            className="mb-6 inline-flex items-center gap-1.5 text-body-sm text-ink-400 transition-colors hover:text-ink-100"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Back to the landing page
          </Link>

          {/* A live session is shown, not silently acted on. */}
          {previous && (
            <div className="mb-6 rounded-2xl border border-er-700/50 bg-er-900 p-4">
              <p className="text-micro font-semibold uppercase tracking-wide text-ink-400">
                Already signed in
              </p>
              <p className="mt-1.5 text-body-sm text-ink-100">
                <span className="font-semibold">{previous.name ?? 'This account'}</span>
                <span className="text-ink-400"> · {ROLE_LABELS[previous.role]}</span>
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  onClick={() => navigate(from ?? '/home', { replace: true })}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-er-500 px-3.5 py-2 text-body-sm font-semibold text-white transition-colors hover:bg-er-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-300"
                >
                  Continue as {previous.name?.split(' ')[0] ?? 'this account'}
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </button>
                <button
                  onClick={switchAccount}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-er-700/60 px-3.5 py-2 text-body-sm font-semibold text-ink-200 transition-colors hover:border-er-500/60 hover:bg-er-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
                >
                  Use another account
                </button>
              </div>
            </div>
          )}

          <h1 className="font-display text-[26px] font-bold leading-tight tracking-tight text-ink-50">
            {mode === 'signup' ? 'Create your account' : 'Sign in'}
          </h1>
          <p className="mt-1.5 text-body-sm text-ink-400">
            {mode === 'signup'
              ? 'Pick the role you are joining as. It decides what you can do.'
              : 'Enter the credentials for your campus account.'}
          </p>

          <form onSubmit={handleAuth} className="mt-6">
            {error && (
              <div
                className="mb-4 flex items-start gap-2.5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-body-sm text-red-200"
                role="alert"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-4">
              {mode === 'signup' && (
                <>
                  <div className="space-y-1.5">
                    <label htmlFor="fullName" className="text-micro font-semibold uppercase tracking-wide text-ink-400">
                      Full name
                    </label>
                    <input
                      id="fullName"
                      type="text"
                      autoComplete="name"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      className={FIELD}
                      placeholder="Aarav Sharma"
                      required
                    />
                  </div>

                  <fieldset className="space-y-1.5">
                    <legend className="text-micro font-semibold uppercase tracking-wide text-ink-400">
                      Joining as
                    </legend>
                    <div className="grid grid-cols-3 gap-2">
                      {SIGNUP_ROLES.map((r) => {
                        const selected = role === r.value
                        return (
                          <button
                            key={r.value}
                            type="button"
                            onClick={() => setRole(r.value)}
                            aria-pressed={selected}
                            className={[
                              'flex flex-col items-center gap-1.5 rounded-xl border px-2 py-3 text-center transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500',
                              selected
                                ? 'border-er-500 bg-er-500/15 text-ink-50'
                                : 'border-er-700/60 bg-er-900 text-ink-300 hover:border-er-600',
                            ].join(' ')}
                          >
                            <r.icon
                              className={selected ? 'h-4 w-4 text-er-300' : 'h-4 w-4 text-ink-400'}
                              aria-hidden="true"
                            />
                            <span className="text-body-sm font-semibold">{r.label}</span>
                          </button>
                        )
                      })}
                    </div>
                    <p className="pt-0.5 text-caption text-ink-500">
                      {SIGNUP_ROLES.find((r) => r.value === role)?.hint}
                    </p>
                  </fieldset>
                </>
              )}

              <div className="space-y-1.5">
                <label htmlFor="email" className="text-micro font-semibold uppercase tracking-wide text-ink-400">
                  Email
                </label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className={FIELD}
                  placeholder="you@college.edu"
                  required
                />
              </div>

              <div className="space-y-1.5">
                <label htmlFor="password" className="text-micro font-semibold uppercase tracking-wide text-ink-400">
                  Password
                </label>
                <div className="relative">
                  <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className={`${FIELD} pr-11`}
                    placeholder="••••••••"
                    required
                    minLength={mode === 'signup' ? 6 : undefined}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-lg p-2 text-ink-400 transition-colors hover:text-ink-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
                  >
                    {showPassword ? (
                      <EyeOff className="h-4 w-4" aria-hidden="true" />
                    ) : (
                      <Eye className="h-4 w-4" aria-hidden="true" />
                    )}
                  </button>
                </div>
                {mode === 'signup' && (
                  <p className="text-caption text-ink-500">At least 6 characters.</p>
                )}
              </div>
            </div>

            <button type="submit" disabled={loading} className={`${CTA} mt-6`}>
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                  {mode === 'signup' ? 'Creating account…' : 'Signing in…'}
                </>
              ) : (
                <>
                  {mode === 'signup' ? 'Create account' : 'Sign in'}
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </>
              )}
            </button>
          </form>

          <p className="mt-6 text-center text-body-sm text-ink-400">
            {mode === 'signup' ? 'Already have an account?' : 'New to Campus Connect?'}
            <button
              onClick={() => {
                setMode(mode === 'signup' ? 'signin' : 'signup')
                setError(null)
              }}
              className="ml-1.5 font-semibold text-er-300 underline-offset-2 hover:text-er-200 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
            >
              {mode === 'signup' ? 'Sign in' : 'Create an account'}
            </button>
          </p>
        </div>
      </main>
    </div>
  )
}
