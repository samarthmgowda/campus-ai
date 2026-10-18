import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Badge } from '../components/ui/Badge'
import { Users, FolderKanban, MessagesSquare, FileText, TrendingUp, PieChart } from 'lucide-react'

interface SkillRow {
  skill?: string
  name?: string
  count?: number
  studentCount?: number
  [k: string]: unknown
}

const BAR_GRADIENTS = [
  'from-campus-500 to-campus-400',
  'from-accent-500 to-accent-400',
  'from-violet-500 to-violet-400',
  'from-emerald-500 to-emerald-400',
  'from-sky-500 to-sky-400',
]

function BarList({ data, labelKey, valueKey }: { data: Record<string, number> | SkillRow[]; labelKey?: string; valueKey?: string }) {
  const entries: [string, number][] = Array.isArray(data)
    ? data.map(r => [String(r[labelKey ?? 'skill'] ?? r.name ?? '?'), Number(r[valueKey ?? 'count'] ?? r.studentCount ?? 0)])
    : Object.entries(data)
  if (entries.length === 0) return <p className="text-caption text-surface-400 text-center py-4">No data yet.</p>
  const max = Math.max(...entries.map(([, v]) => v), 1)
  return (
    <div className="space-y-2.5" role="list" aria-label="Ranked values">
      {entries.slice(0, 8).map(([label, value], i) => (
        <div key={label} role="listitem" className="flex items-center gap-3">
          <span className="text-body-sm font-medium text-surface-700 w-28 truncate flex-shrink-0" title={label}>{label}</span>
          <div className="flex-1 h-2 rounded-full bg-campus-500/10 overflow-hidden" role="img" aria-label={`${label}: ${value}`}>
            <div
              className={`h-full rounded-full bg-gradient-to-r ${BAR_GRADIENTS[i % BAR_GRADIENTS.length]} transition-all duration-500`}
              style={{ width: `${Math.max(5, (value / max) * 100)}%` }}
            />
          </div>
          <span className="text-body-sm font-semibold text-campus-950 tabular-nums w-10 text-right flex-shrink-0">{value}</span>
        </div>
      ))}
    </div>
  )
}

export default function AnalyticsPage() {
  const summary = useQuery({ queryKey: ['analytics-summary'], queryFn: () => api.get<Record<string, unknown>>('/analytics/summary') })
  const skills = useQuery({ queryKey: ['analytics-skills'], queryFn: () => api.get<SkillRow[]>('/analytics/skills?limit=10') })
  const projects = useQuery({ queryKey: ['analytics-projects'], queryFn: () => api.get<Record<string, unknown>>('/analytics/projects') })
  const community = useQuery({ queryKey: ['analytics-community'], queryFn: () => api.get<Record<string, unknown>>('/analytics/community') })
  const availability = useQuery({ queryKey: ['analytics-availability'], queryFn: () => api.get<Record<string, number>>('/analytics/availability') })
  const branches = useQuery({ queryKey: ['analytics-branches'], queryFn: () => api.get<Record<string, number>>('/analytics/branches') })

  const loading = summary.isLoading || skills.isLoading || projects.isLoading || community.isLoading || availability.isLoading || branches.isLoading
  const err = summary.error ?? skills.error ?? projects.error ?? community.error ?? availability.error ?? branches.error

  const totals = summary.data
    ? [
        { icon: Users, label: 'Students', value: summary.data.students ?? summary.data.totalStudents ?? '—' },
        { icon: FolderKanban, label: 'Projects', value: projects.data?.total ?? projects.data?.count ?? summary.data.projects ?? '—' },
        { icon: MessagesSquare, label: 'Posts', value: (community.data?.totalPosts ?? community.data?.posts ?? summary.data.posts ?? '—') as string | number },
        { icon: FileText, label: 'Documents', value: summary.data.documents ?? summary.data.totalDocuments ?? '—' },
      ]
    : []

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="Analytics"
        sub="The pulse of your campus — engagement at a glance"
        actions={summary.data ? <Badge variant="default">Live from backend</Badge> : undefined}
      />

      {loading ? (
        <div className="space-y-4">
          <SkeletonCard />
          <div className="grid gap-4 lg:grid-cols-2"><SkeletonCard /><SkeletonCard /></div>
        </div>
      ) : err ? (
        <ErrorState message={(err as Error).message} onRetry={() => { summary.refetch(); skills.refetch(); projects.refetch(); community.refetch(); availability.refetch(); branches.refetch() }} />
      ) : (
        <>
          <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white px-6 py-6 sm:px-8 mb-5">
            <div className="absolute -top-24 -right-16 h-64 w-64 rounded-full bg-campus-500/25 blur-3xl" aria-hidden="true" />
            <div className="absolute -bottom-28 -left-10 h-52 w-52 rounded-full bg-accent-500/20 blur-3xl" aria-hidden="true" />
            <div className="relative grid grid-cols-2 lg:grid-cols-4 gap-5" role="region" aria-label="Campus totals">
              {totals.map(c => (
                <div key={c.label} className="min-w-0">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 mb-2.5" aria-hidden="true">
                    <c.icon className="h-[18px] w-[18px] text-accent-300" />
                  </span>
                  <p className="font-display font-bold text-[28px] leading-none tabular-nums">{String(c.value)}</p>
                  <p className="text-caption text-white/60 mt-1.5">{c.label}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-2 stagger">
            <section aria-label="Top skills" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5" style={{ ['--stagger' as string]: 0 }}>
              <p className="font-display font-semibold text-[16px] text-campus-950 flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-accent-500" aria-hidden="true" /> Top skills
              </p>
              <p className="text-caption text-surface-400 mt-0.5 mb-4">Most common across students</p>
              {skills.data ? <BarList data={skills.data} /> : <p className="text-caption text-surface-400">No data.</p>}
            </section>

            <section aria-label="Branches" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5" style={{ ['--stagger' as string]: 1 }}>
              <p className="font-display font-semibold text-[16px] text-campus-950 flex items-center gap-2">
                <PieChart className="h-4 w-4 text-campus-500" aria-hidden="true" /> Branches
              </p>
              <p className="text-caption text-surface-400 mt-0.5 mb-4">Students by department</p>
              {branches.data ? <BarList data={branches.data} /> : <p className="text-caption text-surface-400">No data.</p>}
            </section>

            <section aria-label="Availability" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5" style={{ ['--stagger' as string]: 2 }}>
              <p className="font-display font-semibold text-[16px] text-campus-950">Availability</p>
              <p className="text-caption text-surface-400 mt-0.5 mb-4">Who is open to what</p>
              {availability.data ? (
                <div className="flex flex-wrap gap-2">
                  {Object.entries(availability.data).map(([k, v]) => (
                    <span key={k} className="inline-flex items-center gap-1.5 rounded-full bg-campus-50 border border-campus-100 px-3 py-1.5 text-body-sm font-medium text-campus-800">
                      {k}
                      <span className="font-display font-bold tabular-nums text-campus-950">{v}</span>
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-caption text-surface-400">No data.</p>
              )}
            </section>

            <section aria-label="Engagement" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5" style={{ ['--stagger' as string]: 3 }}>
              <p className="font-display font-semibold text-[16px] text-campus-950">Community & projects</p>
              <p className="text-caption text-surface-400 mt-0.5 mb-4">Engagement breakdown</p>
              <div className="space-y-2">
                {community.data && Object.entries(community.data).slice(0, 5).map(([k, v]) => (
                  <div key={k} className="flex items-center justify-between rounded-xl bg-campus-50/60 border border-campus-100/60 px-3.5 py-2 text-body-sm">
                    <span className="text-surface-600 capitalize">{k.replace(/([A-Z])/g, ' $1')}</span>
                    <span className="font-display font-bold text-campus-950 tabular-nums">{String(v)}</span>
                  </div>
                ))}
                {projects.data && Object.entries(projects.data).slice(0, 5).map(([k, v]) => (
                  <div key={k} className="flex items-center justify-between rounded-xl bg-surface-50 border border-surface-200/70 px-3.5 py-2 text-body-sm">
                    <span className="text-surface-600 capitalize">Projects · {k.replace(/([A-Z])/g, ' $1')}</span>
                    <span className="font-semibold text-surface-900 tabular-nums">{String(v)}</span>
                  </div>
                ))}
                {(!community.data || Object.keys(community.data).length === 0) &&
                 (!projects.data || Object.keys(projects.data).length === 0) && (
                  <p className="text-caption text-surface-400 text-center py-4">No data yet.</p>
                )}
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  )
}
