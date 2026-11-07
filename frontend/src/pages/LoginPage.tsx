import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { setSession, getId, type Role } from '../utils/identity'
import {
  Bot,
  AlertTriangle,
  Loader2,
} from 'lucide-react'

const PILLARS = [
  { icon: null as any, label: 'Ask grounded answers' },
  { icon: null as any, label: 'Build projects' },
  { icon: null as any, label: 'Join campus life' },
]

export default function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSigningUp, setIsSigningUp] = useState(false)
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState<Role>('student')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (getId()) {
      navigate('/home')
    }
  }, [navigate])

  const handleAuth = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)

    try {
      type Session = {
        token: string
        id: number
        email: string
        fullName: string
        role: string
        profileId: number | null
      }
      const account = isSigningUp
        ? await api.post<Session>('/auth/signup', { email, password, fullName, role })
        : await api.post<Session>('/auth/login', { email, password })

      setSession(account.token, {
        id: account.id,
        role: account.role as Role,
        name: account.fullName,
        profileId: account.profileId,
      })
      navigate('/home')
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="grid min-h-dvh lg:grid-cols-2 bg-[#f5f7fa]">
      <div className="relative hidden flex-col justify-between overflow-hidden p-12 text-white lg:flex bg-gradient-to-br from-[#667eea] via-[#764ba2] to-[#f093fb]">
        <div className="pointer-events-none absolute inset-0" aria-hidden="true">
          <div className="absolute -right-24 -top-32 h-96 w-96 animate-float-slow rounded-full bg-white/10 blur-3xl" />
          <div className="absolute -bottom-36 -left-20 h-80 w-80 animate-float rounded-full bg-white/[0.08] blur-3xl" />
        </div>

        <div className="relative flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/10 bg-white/10" aria-hidden="true">
            <Bot className="h-5 w-5 text-accent-300" />
          </span>
          <p className="font-display font-bold text-[18px] tracking-tight">Campus Connect</p>
        </div>

        <div className="relative">
          <p className="inline-flex items-center rounded-full border border-white/10 bg-white/10 px-3 py-1 text-micro font-medium text-campus-100">
            MIT Mysore · production env
          </p>
          <h1 className="mt-5 font-display font-bold tracking-tight text-[44px] leading-[1.05]">
            Your campus,
            <br />
            answered.
          </h1>
          <p className="mt-4 max-w-[44ch] text-body leading-relaxed text-white/60">
            Ask in plain words and get answers grounded in campus documents — projects, people, events, and roles in one place.
          </p>
          <div className="mt-7 flex flex-wrap gap-2.5">
            {PILLARS.map(p => (
              <span
                key={p.label}
                className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/[0.07] px-3.5 py-2.5 text-body-sm font-medium text-white/80"
              >
                {p.icon && <p.icon className="h-4 w-4 text-accent-300" aria-hidden="true" />}
                {p.label}
              </span>
            ))}
          </div>
        </div>

        <p className="relative text-caption text-white/40">Answers are grounded in curated documents — never invented.</p>
      </div>

      <div className="flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-md animate-fade-in">
          <div className="mb-7 flex items-center gap-2.5 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-500 text-white">
              <Bot className="h-5 w-5" />
            </div>
            <p className="font-display font-bold text-[18px] tracking-tight text-campus-950">Campus Connect</p>
          </div>

          <h2 className="font-display font-bold leading-tight tracking-tight text-[28px] text-campus-950">
            {isSigningUp ? 'Create an account' : 'Welcome back'}
          </h2>
          <p className="mt-1.5 text-body-sm text-surface-500">
            {isSigningUp ? 'Join the campus community' : 'Enter your credentials to continue'}
          </p>

          <form onSubmit={handleAuth} className="mt-6">
            {error && (
              <div
                className="mb-4 flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-body-sm text-red-700"
                role="alert"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
                {error}
              </div>
            )}

            <div className="space-y-4">
              {isSigningUp && (
                <div className="space-y-1.5">
                  <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Full Name</label>
                  <input
                    type="text"
                    value={fullName}
                    onChange={e => setFullName(e.target.value)}
                    className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500"
                    placeholder="John Doe"
                    required
                  />
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Email or USN</label>
                <input
                  type="text"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500"
                  placeholder="john.doe@college.edu"
                  required
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500"
                  placeholder="••••••••"
                  required
                />
              </div>

              {isSigningUp && (
                <div className="space-y-1.5">
                  <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Role</label>
                  <select
                    value={role}
                    onChange={e => setRole(e.target.value as Role)}
                    className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500"
                  >
                    <option value="student">Student</option>
                    <option value="alumni">Alumni</option>
                    <option value="recruiter">Recruiter</option>
                  </select>
                </div>
              )}
            </div>

            <Button type="submit" variant="accent" className="mt-6 w-full" size="lg" disabled={loading}>
              {loading ? <Loader2 className="h-5 w-5 animate-spin" /> : (isSigningUp ? 'Create Account' : 'Sign In')}
            </Button>
          </form>

          <div className="mt-6 text-center">
            <p className="text-body-sm text-surface-500">
              {isSigningUp ? 'Already have an account?' : 'New to Campus Connect?'}
              <button
                onClick={() => setIsSigningUp(!isSigningUp)}
                className="ml-1.5 font-semibold text-campus-600 hover:text-campus-700 focus:outline-none"
              >
                {isSigningUp ? 'Sign in' : 'Create account'}
              </button>
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
