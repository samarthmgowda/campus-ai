import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { DemoUser } from '../types'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { cn } from '../utils/cn'
import { setIdentity, getId, getRole, ROLE_LABELS } from '../utils/identity'
import { Bot, ArrowRight, CheckCircle2, MessagesSquare, FolderKanban, CalendarDays, AlertTriangle, Loader2 } from 'lucide-react'

/** Composite key: role and id together, so equally-numbered rows stay distinct. */
const userKey = (u: { role: string; id: number }) => `${u.role}:${u.id}`

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
    setIdentity(id, role as 'student' | 'alumni' | 'recruiter', user?.name)
    navigate('/home')
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-[#667eea] to-[#764ba2]">
        <Loader2 className="h-8 w-8 animate-spin text-accent-300" aria-label="Loading" />
      </div>
    )
  }

  return (
    <div className="min-h-screen grid lg:grid-cols-2 bg-[#f5f7fa]">
      {/* Brand panel */}
      <div className="relative hidden lg:flex flex-col justify-between overflow-hidden text-white p-12 bg-gradient-to-br from-[#667eea] via-[#764ba2] to-[#f093fb]">
        <div className="absolute -top-32 -right-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" aria-hidden="true" />
        <div className="absolute -bottom-36 -left-20 h-80 w-80 rounded-full bg-white/[0.08] blur-3xl" aria-hidden="true" />
        <div className="relative flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/10 border border-white/10" aria-hidden="true">
            <Bot className="h-5 w-5 text-accent-300" />
          </span>
          <p className="font-display font-bold text-[18px] tracking-tight">Campus AI</p>
        </div>
        <div className="relative">
          <p className="inline-flex items-center rounded-full bg-white/10 border border-white/10 px-3 py-1 text-micro font-medium text-campus-100">
            MIT Mysore · demo environment
          </p>
          <h1 className="font-display font-bold tracking-tight text-[44px] leading-[1.05] mt-5">
            Your campus,<br />answered.
          </h1>
          <p className="text-body text-white/60 mt-4 max-w-[44ch] leading-relaxed">
            Ask in plain words and get answers grounded in campus documents — projects, people, events, and roles in one place.
          </p>
          <div className="flex gap-2.5 mt-7">
            {PILLARS.map(p => (
              <span key={p.label} className="inline-flex items-center gap-2 rounded-xl bg-white/[0.07] border border-white/10 px-3.5 py-2.5 text-body-sm font-medium text-white/80">
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
          <div className="lg:hidden flex items-center gap-2.5 mb-7">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2]" aria-hidden="true">
              <Bot className="h-5 w-5 text-accent-300" />
            </span>
            <p className="font-display font-bold text-[18px] text-campus-950 tracking-tight">Campus AI</p>
          </div>
          <h2 className="font-display font-bold tracking-tight text-[28px] text-campus-950 leading-tight">Pick who you are today</h2>
          <p className="text-body-sm text-surface-500 mt-1.5">Demo sign-in — every request carries your identity to the backend.</p>

          <form onSubmit={handleSubmit} className="mt-6">
            {error && (
              <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-body-sm text-red-700 flex items-start gap-2.5 mb-4" role="alert">
                <AlertTriangle className="h-4 w-4 flex-shrink-0 mt-0.5" aria-hidden="true" />
                {error}
              </div>
            )}

            <div className="space-y-2 max-h-[320px] overflow-y-auto scrollbar-light pr-0.5" role="group" aria-label="Choose a demo user">
              {users.map(user => {
                const key = userKey(user)
                const active = selectedUser === key
                return (
                  <label
                    key={key}
                    className={cn(
                      'flex items-center gap-3 rounded-2xl border p-3.5 cursor-pointer transition-all duration-150 bg-white',
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
                      className="h-4 w-4 accent-campus-600 flex-shrink-0"
                      aria-label={`Sign in as ${user.name} (${ROLE_LABELS[user.role]})`}
                    />
                    <span className={cn(
                      'flex h-10 w-10 items-center justify-center rounded-full font-display font-bold text-body-sm flex-shrink-0',
                      active ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white' : 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white'
                    )} aria-hidden="true">
                      {user.name.charAt(0).toUpperCase()}
                    </span>
                    <span className="flex-1 text-left min-w-0">
                      <span className="block font-semibold text-body-sm text-campus-950 truncate">{user.name}</span>
                      <span className="block text-caption text-surface-400 mt-0.5">
                        {user.branch && <span>{user.branch} · </span>}
                        {user.year != null && <span>Year {user.year} · </span>}
                        {user.company && <span>{user.company} · </span>}
                        {user.currentRole && <span>{user.currentRole}</span>}
                      </span>
                    </span>
                    {user.role && (
                      <Badge variant="outline" size="sm" className="flex-shrink-0">
                        {ROLE_LABELS[user.role]}
                      </Badge>
                    )}
                    {active && <CheckCircle2 className="h-5 w-5 text-campus-600 flex-shrink-0" aria-hidden="true" />}
                  </label>
                )
              })}
            </div>

            <Button type="submit" variant="accent" className="w-full mt-5" size="lg" disabled={!selectedUser}>
              <span>Enter campus</span>
              <ArrowRight className="h-5 w-5" aria-hidden="true" />
            </Button>
          </form>

          <p className="mt-5 text-center text-caption text-surface-400">
            Demo accounts reset on server restart.
          </p>
        </div>
      </div>
    </div>
  )
}
