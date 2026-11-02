import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { DemoUser } from '../types'
import { Button } from '../components/ui/Button'
import { cn } from '../utils/cn'
import { setIdentity, getId, getRole, ROLE_LABELS, type Role } from '../utils/identity'
import { Avatar, IconChip } from '../components/ui/Chip'
import {
  Bot,
  ArrowRight,
  CheckCircle2,
  MessagesSquare,
  FolderKanban,
  CalendarDays,
  AlertTriangle,
  Loader2,
} from 'lucide-react'

/** Composite key: role and id together, so equally-numbered rows stay distinct. */
const userKey = (u: { role: string; id: number }) => `${u.role}:${u.id}`

const ROLE_ORDER: Role[] = ['student', 'alumni', 'recruiter']

/**
 * Group headings for the picker.
 *
 * Kept separate from ROLE_LABELS because "Alumni" is already plural — deriving
 * the heading by appending "s" produced "ALUMNIS".
 */
const ROLE_PLURALS: Record<Role, string> = {
  student: 'Students',
  alumni: 'Alumni',
  recruiter: 'Recruiters',
}

/**
 * The one-line meta under each name.
 *
 * Role-aware because the same `year` field means different things per table:
 * a student's year is their current year of study, an alumnus's is their
 * graduation year. Rendering both as "Year N" would read "Year 2021" for alumni.
 * Built as a joined list rather than inline separators so a missing field can't
 * leave a dangling "·".
 */
function userMeta(u: DemoUser): string {
  const parts: string[] = []
  if (u.role === 'recruiter') {
    if (u.roleTitle) parts.push(u.roleTitle)
    if (u.company) parts.push(u.company)
  } else {
    if (u.branch) parts.push(u.branch)
    if (u.year != null) parts.push(u.role === 'alumni' ? `Class of ${u.year}` : `Year ${u.year}`)
    if (u.role === 'alumni') {
      if (u.currentRole) parts.push(u.currentRole)
      if (u.currentCompany) parts.push(`@ ${u.currentCompany}`)
    }
  }
  return parts.join(' · ')
}

const PILLARS = [
  { icon: MessagesSquare, label: 'Ask grounded answers' },
  { icon: FolderKanban, label: 'Build projects' },
  { icon: CalendarDays, label: 'Join campus life' },
]

export default function LoginPage() {
  const navigate = useNavigate()
  const [users, setUsers] = useState<DemoUser[]>([])
  const [selectedUser, setSelectedUser] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.get<DemoUser[]>('/demo/users')
      .then(setUsers)
      .catch(() => setError('Could not load demo users. Is the backend running on port 8080?'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const savedId = getId()
    const savedRole = getRole()
    const savedKey = savedId ? `${savedRole ?? 'student'}:${savedId}` : null
    if (savedKey && users.some(u => userKey(u) === savedKey)) {
      setSelectedUser(savedKey)
    } else if (users.length > 0) {
      setSelectedUser(userKey(users[0]))
    }
  }, [users])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedUser) return
    const [role, id] = selectedUser.split(':')
    const user = users.find(u => userKey(u) === selectedUser)
    setIdentity(id, role as Role, user?.name)
    navigate('/home')
  }

  if (loading) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-[#f5f7fa]">
        <Loader2 className="h-8 w-8 animate-spin text-campus-500" aria-label="Loading" />
      </div>
    )
  }

  return (
    <div className="grid min-h-dvh lg:grid-cols-2 bg-[#f5f7fa]">
      {/* Brand panel — the only place the product states its promise in full. */}
      <div className="relative hidden flex-col justify-between overflow-hidden p-12 text-white lg:flex bg-gradient-to-br from-[#667eea] via-[#764ba2] to-[#f093fb]">
        <div className="pointer-events-none absolute inset-0" aria-hidden="true">
          <div className="absolute -right-24 -top-32 h-96 w-96 animate-float-slow rounded-full bg-white/10 blur-3xl" />
          <div className="absolute -bottom-36 -left-20 h-80 w-80 animate-float rounded-full bg-white/[0.08] blur-3xl" />
        </div>

        <div className="relative flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/10 bg-white/10" aria-hidden="true">
            <Bot className="h-5 w-5 text-accent-300" />
          </span>
          <p className="font-display font-bold text-[18px] tracking-tight">Campus AI</p>
        </div>

        <div className="relative">
          <p className="inline-flex items-center rounded-full border border-white/10 bg-white/10 px-3 py-1 text-micro font-medium text-campus-100">
            MIT Mysore · demo environment
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
                <p.icon className="h-4 w-4 text-accent-300" aria-hidden="true" />
                {p.label}
              </span>
            ))}
          </div>
        </div>

        <p className="relative text-caption text-white/40">Answers are grounded in curated documents — never invented.</p>
      </div>

      {/* Picker panel */}
      <div className="flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-md animate-fade-in">
          <div className="mb-7 flex items-center gap-2.5 lg:hidden">
            <IconChip icon={Bot} size="lg" className="h-10 w-10" />
            <p className="font-display font-bold text-[18px] tracking-tight text-campus-950">Campus AI</p>
          </div>

          <h2 className="font-display font-bold leading-tight tracking-tight text-[28px] text-campus-950">
            Pick who you are today
          </h2>
          <p className="mt-1.5 text-body-sm text-surface-500">
            Demo sign-in — every request carries your identity to the backend.
          </p>

          <form onSubmit={handleSubmit} className="mt-6">
            {error && (
              <div
                className="mb-4 flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-body-sm text-red-700"
                role="alert"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
                {error}
              </div>
            )}

            <fieldset className="max-h-[340px] space-y-4 overflow-y-auto pr-0.5 scrollbar-light">
              <legend className="sr-only">Choose a demo user</legend>
              {ROLE_ORDER.map(role => {
                const group = users.filter(u => u.role === role)
                if (group.length === 0) return null
                return (
                  <div key={role}>
                    <p className="mb-1.5 px-1 text-micro font-semibold tracking-wide text-surface-400">
                      {ROLE_PLURALS[role].toUpperCase()}
                    </p>
                    <div className="space-y-2">
                      {group.map(user => {
                        const key = userKey(user)
                        const active = selectedUser === key
                        return (
                          <label
                            key={key}
                            className={cn(
                              'flex cursor-pointer items-center gap-3 rounded-2xl border bg-white p-3.5 transition-all duration-150',
                              'has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-campus-500',
                              active
                                ? 'border-campus-500 shadow-card ring-1 ring-campus-500'
                                : 'border-campus-900/[0.08] shadow-card hover:border-campus-900/25'
                            )}
                          >
                            <input
                              type="radio"
                              name="demoUser"
                              value={key}
                              checked={active}
                              onChange={() => setSelectedUser(key)}
                              className="h-4 w-4 flex-shrink-0 accent-campus-600"
                              aria-label={`Sign in as ${user.name} (${ROLE_LABELS[user.role]})`}
                            />
                            <Avatar name={user.name} size="lg" />
                            <span className="min-w-0 flex-1 text-left">
                              <span className="block truncate text-body-sm font-semibold text-campus-950">
                                {user.name}
                              </span>
                              <span className="mt-0.5 block truncate text-caption text-surface-400">
                                {userMeta(user)}
                              </span>
                            </span>
                            {active && <CheckCircle2 className="h-5 w-5 flex-shrink-0 text-campus-600" aria-hidden="true" />}
                          </label>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
            </fieldset>

            <Button type="submit" variant="accent" className="mt-5 w-full" size="lg" disabled={!selectedUser}>
              <span>Enter campus</span>
              <ArrowRight className="h-5 w-5" aria-hidden="true" />
            </Button>
          </form>

          <p className="mt-5 text-center text-caption text-surface-400">Demo accounts reset on server restart.</p>
        </div>
      </div>
    </div>
  )
}
