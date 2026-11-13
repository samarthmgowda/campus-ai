import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { PageContainer } from '../components/ui/PageContainer'
import { api } from '../api/client'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { useSession, ROLE_LABELS, type Role } from '../utils/identity'
import { TrendingUp, CheckCircle2, Circle, Sparkles, Pencil, X, Star, Loader2, Github, Linkedin } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'

function Ring({ score }: { score: number }) {
  const r = 34
  const c = 2 * Math.PI * r
  const filled = Math.max(0, Math.min(100, score))
  return (
    <div className="relative h-[92px] w-[92px] flex-shrink-0" role="progressbar" aria-valuenow={filled} aria-valuemin={0} aria-valuemax={100} aria-label="Profile completeness">
      <svg viewBox="0 0 92 92" className="h-full w-full -rotate-90" aria-hidden="true">
        <circle cx="46" cy="46" r={r} fill="none" stroke="rgba(255,255,255,0.12)" strokeWidth="9" />
        <circle
          cx="46" cy="46" r={r} fill="none"
          stroke="url(#ringGrad)" strokeWidth="9" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c - (c * filled) / 100}
          className="transition-all duration-700"
        />
        <defs>
          <linearGradient id="ringGrad" x1="0" y1="0" x2="92" y2="92">
            <stop offset="0%" stopColor="#667eea" />
            <stop offset="100%" stopColor="#764ba2" />
          </linearGradient>
        </defs>
      </svg>
      <span className="absolute inset-0 flex items-center justify-center font-display font-bold text-[20px] text-white tabular-nums">
        {filled}%
      </span>
    </div>
  )
}

interface FootprintStat { label: string; value: string }

function studentFootprint(me: Record<string, any>): FootprintStat[] {
  const num = (v: unknown): number | null => (typeof v === 'number' ? v : null)
  const stat = (label: string, value: number | null): FootprintStat | null =>
    value === null ? null : { label, value: String(value) }

  return [
    stat('Projects', num(me.projects?.count)),
    stat('Invites', num(me.pendingInvites?.count)),
    stat('Event RSVPs', num(me.rsvps?.count)),
    stat('Posts', num(me.posts?.count)),
    stat('Reviews', num(me.reputation?.reviewCount)),
    stat('Followers', num(me.connections?.followers)),
    stat('Following', num(me.connections?.following)),
  ].filter((s): s is FootprintStat => s !== null)
}

export default function ProfilePage() {
  const session = useSession()
  const role: Role = session?.role ?? 'student'
  const isStudent = role === 'student'
  const queryClient = useQueryClient()
  const [isEditing, setIsEditing] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [editForm, setEditForm] = useState({
    name: '',
    bio: '',
    skills: '',
    interests: '',
    branch: '',
    year: '',
    githubUrl: '',
    linkedinUrl: '',
  })

  // Every key names its owner: two students share a role but not a profile.
  const account = session?.userId ?? null

  const { data: me, isLoading: meLoading, error: meError, refetch } = useQuery({
    queryKey: ['me', account, role],
    queryFn: () => api.get<Record<string, any>>('/me'),
    enabled: !!account,
  })

  const { data: completeness } = useQuery({
    queryKey: ['completeness', account],
    queryFn: () => api.get<{ score: number; missingFields: string[]; level: string }>('/me/completeness'),
    enabled: isStudent && !!account,
  })

  const { data: recs } = useQuery({
    queryKey: ['recommendations', account],
    queryFn: () => api.get<{ recommendations: { skill: string; count: number; reason: string }[] }>('/me/recommendations?limit=5'),
    enabled: isStudent && !!account,
  })

  const updateMutation = useMutation({
    // Backend route is PUT /me (see ProfileController.updateMe). Sending the
    // body as the update map — the earlier /profiles/me path 404'd, so every
    // save silently failed and the form appeared to do nothing.
    mutationFn: (updates: Record<string, unknown>) => api.put('/me', updates),
    onSuccess: () => {
      setSaveError(null)
      setIsEditing(false)
      // Refresh the profile, the completeness ring and the recommendations —
      // all three are derived from the fields we just changed.
      queryClient.invalidateQueries({ queryKey: ['me'] })
      queryClient.invalidateQueries({ queryKey: ['completeness'] })
      queryClient.invalidateQueries({ queryKey: ['recommendations'] })
      refetch()
    },
    onError: (e: Error) => setSaveError(e.message),
  })

  if (meLoading) {
    return (
      <PageContainer width="reading" className="space-y-4">
        <SkeletonCard /><SkeletonCard />
      </PageContainer>
    )
  }

  if (meError) {
    return (
      <PageContainer width="reading">
        <PageHeader title="Profile" sub="Your campus identity" />
        <ErrorState message={(meError as Error).message} onRetry={() => refetch()} />
      </PageContainer>
    )
  }

  const profile: Record<string, any> = isStudent ? (me?.profile ?? {}) : (me ?? {})
  const name = String(profile.name ?? '—')
  const skillList = profile.skills ? String(profile.skills).split(',').map(s => s.trim()).filter(Boolean) : []
  const attributeLine = isStudent
    ? [profile.branch, profile.year != null ? `Year ${profile.year}` : null].filter(Boolean).join(' · ')
    : [
        profile.branch,
        profile.graduationYear != null ? `Grad ${profile.graduationYear}` : null,
        profile.currentRole,
        profile.currentCompany,
        profile.company,
        profile.roleTitle,
      ].filter(Boolean).join(' · ')

  const footprint = isStudent && me ? studentFootprint(me) : []

  const handleEditToggle = () => {
    setSaveError(null)
    setEditForm({
      name: profile.name ?? '',
      bio: profile.bio ?? '',
      skills: profile.skills ?? '',
      interests: profile.interests ?? '',
      branch: profile.branch ?? '',
      year: profile.year != null ? String(profile.year) : '',
      githubUrl: profile.githubUrl ?? '',
      linkedinUrl: profile.linkedinUrl ?? '',
    })
    setIsEditing(!isEditing)
  }

  const handleSave = async () => {
    setSaveError(null)
    // Only send fields the user actually filled — the backend ignores unknown
    // keys but treats an empty string as a real value, so blanking a field the
    // student never set would be destructive. Year is sent as a number.
    const payload: Record<string, unknown> = {}
    const put = (key: string, value: string) => {
      const v = value.trim()
      if (v) payload[key] = key === 'year' ? Number(v) : v
    }
    put('name', editForm.name)
    put('branch', editForm.branch)
    put('year', editForm.year)
    put('skills', editForm.skills)
    put('interests', editForm.interests)
    put('bio', editForm.bio)
    put('githubUrl', editForm.githubUrl)
    put('linkedinUrl', editForm.linkedinUrl)

    if (Object.keys(payload).length === 0) {
      setSaveError('Nothing to save — fill in at least one field.')
      return
    }

    try {
      await updateMutation.mutateAsync(payload)
    } catch {
      // onError already surfaced the message
    }
  }

  return (
    <PageContainer width="reading">
      <PageHeader title="Profile" sub="Your campus identity and growth signals" />

      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white p-6 sm:p-8 mb-5">
        <div className="absolute -top-20 -right-20 h-60 w-60 rounded-full bg-campus-500/25 blur-3xl" aria-hidden="true" />
        <div className="absolute -bottom-24 -left-12 h-52 w-52 rounded-full bg-accent-500/20 blur-3xl" aria-hidden="true" />
        <div className="relative flex items-start gap-5">
          <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-white/15 border border-white/20 font-display font-bold text-[26px] flex-shrink-0 ring-2 ring-white/20" aria-hidden="true">
            {name.charAt(0).toUpperCase()}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2.5 flex-wrap">
              <p className="font-display font-bold text-[24px] tracking-tight leading-tight">{name}</p>
              <span className="rounded-full bg-white/10 border border-white/15 px-2.5 py-0.5 text-micro font-medium text-campus-100">
                {ROLE_LABELS[role]}
              </span>
              {profile.rating != null && (
                <div className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-white/10 border border-white/15 text-white">
                  <Star className="h-3 w-3 fill-accent-300 text-accent-300" />
                  <span className="text-micro font-bold">{profile.rating}</span>
                </div>
              )}
            </div>
            {attributeLine && <p className="text-body-sm text-white/60 mt-1">{attributeLine}</p>}
            {!!profile.bio && <p className="text-body-sm text-white/80 mt-2.5 leading-relaxed">{profile.bio}</p>}
            {skillList.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-3.5">
                {skillList.map(s => (
                  <span key={s} className="rounded-full bg-white/10 border border-white/10 px-2.5 py-1 text-micro font-medium text-campus-100">{s}</span>
                ))}
              </div>
            )}
            {(profile.githubUrl || profile.linkedinUrl) && (
              <div className="flex flex-wrap gap-3 mt-3.5 text-body-sm text-white/70">
                {profile.githubUrl && (
                  <span className="inline-flex items-center gap-1.5"><Github className="h-3.5 w-3.5" aria-hidden="true" />{profile.githubUrl}</span>
                )}
                {profile.linkedinUrl && (
                  <span className="inline-flex items-center gap-1.5"><Linkedin className="h-3.5 w-3.5" aria-hidden="true" />{profile.linkedinUrl}</span>
                )}
              </div>
            )}
          </div>
          <div className="flex flex-col items-end gap-3 flex-shrink-0">
            {isStudent && completeness && <Ring score={completeness.score} />}
            {!isEditing && (
              <button
                onClick={handleEditToggle}
                className="inline-flex items-center gap-1.5 rounded-xl bg-white/15 border border-white/20 px-3.5 py-2 text-body-sm font-semibold text-white hover:bg-white/25 transition-colors"
              >
                <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
                Edit profile
              </button>
            )}
          </div>
        </div>
      </div>

      {isEditing && (
        <section className="rounded-2xl border border-campus-300 bg-white shadow-elevated p-5 mb-5 animate-slide-down">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-display font-bold text-[16px] text-campus-950">Edit Profile</h3>
            <button onClick={() => setIsEditing(false)} className="text-surface-400 hover:text-surface-600" aria-label="Close editor">
              <X className="h-4 w-4" />
            </button>
          </div>

          {saveError && (
            <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-body-sm text-red-700" role="alert">
              {saveError}
            </div>
          )}

          <div className="grid gap-4">
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Full name</label>
              <Input value={editForm.name} onChange={e => setEditForm({ ...editForm, name: e.target.value })} placeholder="Your name" />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Branch</label>
                <Input value={editForm.branch} onChange={e => setEditForm({ ...editForm, branch: e.target.value })} placeholder="CSE" />
              </div>
              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Year (1–4)</label>
                <Input value={editForm.year} onChange={e => setEditForm({ ...editForm, year: e.target.value })} placeholder="3" inputMode="numeric" />
              </div>
            </div>
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Skills (comma separated)</label>
              <Input value={editForm.skills} onChange={e => setEditForm({ ...editForm, skills: e.target.value })} placeholder="React, Java, Python" />
            </div>
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Interests (comma separated)</label>
              <Input value={editForm.interests} onChange={e => setEditForm({ ...editForm, interests: e.target.value })} placeholder="Open source, Robotics, Design" />
            </div>
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Bio</label>
              <textarea
                className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500 resize-none"
                rows={3}
                value={editForm.bio}
                onChange={e => setEditForm({ ...editForm, bio: e.target.value })}
                placeholder="Tell us about yourself..."
              />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">GitHub</label>
                <Input value={editForm.githubUrl} onChange={e => setEditForm({ ...editForm, githubUrl: e.target.value })} placeholder="https://github.com/you" />
              </div>
              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">LinkedIn</label>
                <Input value={editForm.linkedinUrl} onChange={e => setEditForm({ ...editForm, linkedinUrl: e.target.value })} placeholder="https://linkedin.com/in/you" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-2">
              <Button variant="outline" size="sm" onClick={() => setIsEditing(false)}>Cancel</Button>
              <Button variant="accent" size="sm" onClick={handleSave} disabled={updateMutation.isPending}>
                {updateMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Save Changes'}
              </Button>
            </div>
          </div>
        </section>
      )}

      {isStudent && completeness && (
        <section aria-label="Completeness" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5 mb-4">
          <div className="flex items-center justify-between">
            <p className="font-display font-semibold text-[16px] text-campus-950">Profile completeness</p>
            <Badge variant={completeness.missingFields.length === 0 ? 'success' : 'default'} size="sm">{completeness.level}</Badge>
          </div>
          {completeness.missingFields.length > 0 ? (
            <ul className="mt-3.5 space-y-2">
              {completeness.missingFields.map(f => (
                <li key={f} className="flex items-center gap-2.5 rounded-xl bg-campus-50/70 border border-campus-100/70 px-3.5 py-2.5 text-body-sm text-surface-600">
                  <Circle className="h-4 w-4 text-campus-300 flex-shrink-0" aria-hidden="true" />
                  Add your <span className="font-semibold text-campus-900">{f}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-3.5 flex items-center gap-2 text-body-sm font-medium text-emerald-700">
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> Everything filled — looking sharp
            </p>
          )}
        </section>
      )}

      {isStudent && recs && recs.recommendations.length > 0 && (
        <section aria-label="Recommended skills" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5 mb-4">
          <p className="font-display font-semibold text-[16px] text-campus-950 flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-accent-500" aria-hidden="true" /> Skills to grow
          </p>
          <p className="text-caption text-surface-400 mt-0.5 mb-3.5">Suggested from how your skills pair across campus</p>
          <div className="space-y-2">
            {recs.recommendations.map(r => (
              <div key={r.skill} className="flex items-center gap-3 rounded-xl bg-surface-50/70 border border-surface-200/70 px-3.5 py-2.5">
                <TrendingUp className="h-4 w-4 text-accent-500 flex-shrink-0" aria-hidden="true" />
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-surface-900 text-body-sm">{r.skill}</p>
                  <p className="text-caption text-surface-500 truncate">{r.reason}</p>
                </div>
                <Badge variant="outline" size="sm"><span className="tabular-nums">{r.count}</span></Badge>
              </div>
            ))}
          </div>
        </section>
      )}

      {footprint.length > 0 && (
        <section aria-label="Activity" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5">
          <p className="font-display font-semibold text-[16px] text-//campus-950 mb-3.5">Your footprint</p>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
            {footprint.map(s => (
              <div key={s.label} className="rounded-xl bg-campus-50/60 border border-campus-100/60 px-3.5 py-3">
                <p className="font-display font-bold text-[20px] text-campus-950 tabular-nums leading-none">{s.value}</p>
                <p className="text-caption text-surface-500 mt-1.5">{s.label}</p>
              </div>
            ))}
          </div>
        </section>
      )}
    </PageContainer>
  )
}
