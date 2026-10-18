import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { TrendingUp, CheckCircle2, Circle, Sparkles } from 'lucide-react'

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

export default function ProfilePage() {
  const { data: me, isLoading: meLoading, error: meError, refetch } = useQuery({
    queryKey: ['me'],
    queryFn: () => api.get<Record<string, unknown>>('/me'),
  })

  const { data: completeness } = useQuery({
    queryKey: ['completeness'],
    queryFn: () => api.get<{ score: number; missingFields: string[]; level: string }>('/me/completeness'),
  })

  const { data: recs } = useQuery({
    queryKey: ['recommendations'],
    queryFn: () => api.get<{ recommendations: { skill: string; count: number; reason: string }[] }>('/me/recommendations?limit=5'),
  })

  if (meLoading) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <SkeletonCard /><SkeletonCard />
      </div>
    )
  }

  if (meError) {
    return (
      <div className="mx-auto max-w-3xl">
        <PageHeader title="Profile" sub="Your campus identity" />
        <ErrorState message={(meError as Error).message} onRetry={() => refetch()} />
      </div>
    )
  }

  const profile = me as Record<string, unknown> | undefined
  const name = String(profile?.name ?? '—')
  const skillList = profile?.skills ? String(profile.skills).split(',').map(s => s.trim()).filter(Boolean) : []
  const counts = profile
    ? Object.entries(profile).filter(([k]) => !['name', 'branch', 'year', 'bio', 'skills', 'id', 'suspended'].includes(k))
    : []

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Profile" sub="Your campus identity and growth signals" />

      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white p-6 sm:p-8 mb-5">
        <div className="absolute -top-20 -right-20 h-60 w-60 rounded-full bg-campus-500/25 blur-3xl" aria-hidden="true" />
        <div className="absolute -bottom-24 -left-12 h-52 w-52 rounded-full bg-accent-500/20 blur-3xl" aria-hidden="true" />
        <div className="relative flex items-start gap-5">
          <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-[#667eea] to-[#764ba2] font-display font-bold text-[26px] flex-shrink-0 ring-2 ring-white/20" aria-hidden="true">
            {name.charAt(0).toUpperCase()}
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-display font-bold text-[24px] tracking-tight leading-tight">{name}</p>
            <p className="text-body-sm text-white/60 mt-1">
              {String(profile?.branch ?? '')}{profile?.year ? ` · Year ${String(profile.year)}` : ''}
            </p>
            {!!profile?.bio && <p className="text-body-sm text-white/80 mt-2.5 leading-relaxed">{String(profile.bio)}</p>}
            {skillList.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-3.5">
                {skillList.map(s => (
                  <span key={s} className="rounded-full bg-white/10 border border-white/10 px-2.5 py-1 text-micro font-medium text-campus-100">{s}</span>
                ))}
              </div>
            )}
          </div>
          {completeness && <Ring score={completeness.score} />}
        </div>
      </div>

      {completeness && (
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

      {recs && recs.recommendations.length > 0 && (
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

      {counts.length > 0 && (
        <section aria-label="Activity" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5">
          <p className="font-display font-semibold text-[16px] text-campus-950 mb-3.5">Your footprint</p>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
            {counts.map(([k, v]) => (
              <div key={k} className="rounded-xl bg-campus-50/60 border border-campus-100/60 px-3.5 py-3">
                <p className="font-display font-bold text-[20px] text-campus-950 tabular-nums leading-none">{String(v)}</p>
                <p className="text-caption text-surface-500 capitalize mt-1.5">{k.replace(/([A-Z])/g, ' $1')}</p>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
