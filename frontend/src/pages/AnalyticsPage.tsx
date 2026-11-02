import { useQuery } from '@tanstack/react-query'
import { PageContainer } from '../components/ui/PageContainer'
import { api } from '../api/client'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Badge } from '../components/ui/Badge'
import { Panel } from '../components/ui/Panel'
import { Banner, BannerStat } from '../components/ui/Banner'
import { BarList, toBarData } from '../components/ui/BarList'
import { IconChip } from '../components/ui/Chip'
import { Reveal } from '../components/ui/Reveal'
import { Users, FolderKanban, MessagesSquare, FileText, TrendingUp, PieChart, Clock, Activity } from 'lucide-react'

interface SkillRow {
  skill?: string
  name?: string
  count?: number
  studentCount?: number
  [k: string]: unknown
}

export default function AnalyticsPage() {
  const summary = useQuery({ queryKey: ['analytics-summary'], queryFn: () => api.get<Record<string, unknown>>('/analytics/summary') })
  const skills = useQuery({ queryKey: ['analytics-skills'], queryFn: () => api.get<SkillRow[]>('/analytics/skills?limit=10') })
  const projects = useQuery({ queryKey: ['analytics-projects'], queryFn: () => api.get<Record<string, unknown>>('/analytics/projects') })
  const community = useQuery({ queryKey: ['analytics-community'], queryFn: () => api.get<Record<string, unknown>>('/analytics/community') })
  const availability = useQuery({ queryKey: ['analytics-availability'], queryFn: () => api.get<Record<string, number>>('/analytics/availability') })
  const branches = useQuery({ queryKey: ['analytics-branches'], queryFn: () => api.get<Record<string, number>>('/analytics/branches') })

  const queries = [summary, skills, projects, community, availability, branches]
  const loading = queries.some((q) => q.isLoading)
  const err = summary.error ?? skills.error ?? projects.error ?? community.error ?? availability.error ?? branches.error

  const totals = summary.data
    ? [
        { icon: Users, label: 'Students', value: summary.data.students ?? summary.data.totalStudents ?? '—' },
        { icon: FolderKanban, label: 'Projects', value: projects.data?.total ?? projects.data?.count ?? summary.data.projects ?? '—' },
        { icon: MessagesSquare, label: 'Posts', value: community.data?.totalPosts ?? community.data?.posts ?? summary.data.posts ?? '—' },
        { icon: FileText, label: 'Documents', value: summary.data.documents ?? summary.data.totalDocuments ?? '—' },
      ]
    : []

  return (
    <PageContainer width="standard">
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
        <ErrorState message={(err as Error).message} onRetry={() => queries.forEach((q) => q.refetch())} />
      ) : (
        <>
          {/* The one loud surface on the page: the campus-wide totals. */}
          <Reveal>
            <Banner className="mb-5">
              <div className="grid grid-cols-2 gap-5 lg:grid-cols-4" role="region" aria-label="Campus totals">
                {totals.map((c) => (
                  <div key={c.label} className="min-w-0">
                    <IconChip icon={c.icon} fill="ghost" size="md" className="mb-2.5" />
                    <BannerStat value={String(c.value)} label={c.label} />
                  </div>
                ))}
              </div>
            </Banner>
          </Reveal>

          <div className="grid gap-4 lg:grid-cols-2">
            <Reveal>
              <Panel className="h-full">
                <PanelHeaderBlock
                  icon={<TrendingUp className="h-4 w-4 text-accent-500" aria-hidden="true" />}
                  title="Top skills"
                  description="Most common across students"
                />
                {skills.data ? (
                  <BarList data={toBarData(skills.data)} />
                ) : (
                  <p className="text-caption text-surface-400">No data.</p>
                )}
              </Panel>
            </Reveal>

            <Reveal delay={80}>
              <Panel className="h-full">
                <PanelHeaderBlock
                  icon={<PieChart className="h-4 w-4 text-campus-500" aria-hidden="true" />}
                  title="Branches"
                  description="Students by department"
                />
                {branches.data ? (
                  <BarList data={toBarData(branches.data)} />
                ) : (
                  <p className="text-caption text-surface-400">No data.</p>
                )}
              </Panel>
            </Reveal>

            <Reveal delay={120}>
              <Panel className="h-full">
                <PanelHeaderBlock
                  icon={<Clock className="h-4 w-4 text-emerald-500" aria-hidden="true" />}
                  title="Availability"
                  description="Who is open to what"
                />
                {availability.data ? (
                  <div className="flex flex-wrap gap-2">
                    {Object.entries(availability.data).map(([k, v]) => (
                      <span
                        key={k}
                        className="inline-flex items-center gap-1.5 rounded-full border border-campus-100 bg-campus-50 px-3 py-1.5 text-body-sm font-medium text-campus-800"
                      >
                        {k}
                        <span className="font-display font-bold tabular-nums text-campus-950">{v}</span>
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="text-caption text-surface-400">No data.</p>
                )}
              </Panel>
            </Reveal>

            <Reveal delay={160}>
              <Panel className="h-full">
                <PanelHeaderBlock
                  icon={<Activity className="h-4 w-4 text-violet-500" aria-hidden="true" />}
                  title="Community & projects"
                  description="Engagement breakdown"
                />
                <div className="space-y-2">
                  {community.data && Object.entries(community.data).slice(0, 5).map(([k, v]) => (
                    <MetricRow key={k} label={k.replace(/([A-Z])/g, ' $1')} value={String(v)} />
                  ))}
                  {projects.data && Object.entries(projects.data).slice(0, 5).map(([k, v]) => (
                    <MetricRow key={k} label={`Projects · ${k.replace(/([A-Z])/g, ' $1')}`} value={String(v)} muted />
                  ))}
                  {(!community.data || Object.keys(community.data).length === 0) &&
                    (!projects.data || Object.keys(projects.data).length === 0) && (
                      <p className="py-4 text-center text-caption text-surface-400">No data yet.</p>
                    )}
                </div>
              </Panel>
            </Reveal>
          </div>
        </>
      )}
    </PageContainer>
  )
}

/** Title row shared by the four panels, so their headings sit at one height. */
function PanelHeaderBlock({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode
  title: string
  description: string
}) {
  return (
    <div className="mb-4">
      <p className="flex items-center gap-2 font-display font-semibold text-[16px] text-campus-950">
        {icon}
        {title}
      </p>
      <p className="mt-0.5 text-caption text-surface-400">{description}</p>
    </div>
  )
}

function MetricRow({ label, value, muted = false }: { label: string; value: string; muted?: boolean }) {
  return (
    <div
      className={
        muted
          ? 'flex items-center justify-between rounded-xl border border-surface-200/70 bg-surface-50 px-3.5 py-2 text-body-sm'
          : 'flex items-center justify-between rounded-xl border border-campus-100/60 bg-campus-50/60 px-3.5 py-2 text-body-sm'
      }
    >
      <span className="capitalize text-surface-600">{label}</span>
      <span className={muted ? 'font-semibold tabular-nums text-surface-900' : 'font-display font-bold tabular-nums text-campus-950'}>
        {value}
      </span>
    </div>
  )
}
