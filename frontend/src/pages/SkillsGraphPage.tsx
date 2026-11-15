// import { useState } from 'react'  // unused
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { getId } from '../utils/identity'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { PageContainer } from '../components/ui/PageContainer'
import { cn } from '../utils/cn'

interface TopSkill {
  skill: string
  count: number
}

interface TopRelated {
  skill: string
  related: string
  count: number
}

interface SkillToGrow {
  skill: string
  fromSkill: string
  cooccurrence: number
  category: string
}

interface DashboardData {
  topSkills: TopSkill[]
  topRelated: TopRelated[]
  categoryDistribution: Record<string, number>
  skillsToGrow: SkillToGrow[]
  totalSkills: number
  totalProfiles: number
}

function myId(): number | null {
  const v = getId()
  return v ? Number(v) : null
}

export default function SkillsGraphPage() {
  const userId = myId()

  const {
    data: dash,
    isLoading,
    isError,
    refetch,
  } = useQuery<DashboardData>({
    queryKey: ['skills-dashboard', userId],
    queryFn: () => api.get<DashboardData>('/skills/dashboard'),
    staleTime: 60_000,
  })

  if (isError) {
    return (
      <PageContainer>
        <ErrorState
          title="Could not load skill graph"
          message="The skill graph data could not be loaded."
          onRetry={() => refetch()}
        />
      </PageContainer>
    )
  }

  const categories = Object.entries(dash?.categoryDistribution ?? {})
    .filter(([, v]) => v > 0)
    .sort(([, a], [, b]) => b - a)

  return (
    <PageContainer>
      <PageHeader
        title="Skill Graph"
        sub="How skills connect across campus — and what to learn next"
        actions={
          dash ? <Badge variant="default">{dash.totalSkills || 0} skills tracked</Badge> : undefined
        }
      />

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map(i => <SkeletonCard key={i} />)}
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <GraphCard
            title="Top skills on campus"
            description="Most commonly listed by students"
            className="lg:col-span-2"
          >
            {dash?.topSkills?.length ? (
              <div className="flex flex-wrap gap-2">
                {dash.topSkills.map((s: TopSkill) => (
                  <span
                    key={s.skill}
                    className="inline-flex items-center gap-1.5 rounded-full bg-er-900 border border-white/10 px-2.5 py-1"
                  >
                    <span className="text-caption font-semibold text-er-300">{s.skill}</span>
                    <span className="text-micro text-er-400 tabular-nums">{s.count}</span>
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-body-sm text-ink-400">No skills recorded yet.</p>
            )}
          </GraphCard>

          <GraphCard title="Campus reach" description="Coverage across profiles">
            <div className="flex items-baseline gap-2">
              <span className="font-display font-bold text-[34px] leading-none tabular-nums text-ink-50">
                {dash?.totalProfiles || 0}
              </span>
              <span className="text-body-sm text-ink-400">profiles with skills</span>
            </div>
            <p className="text-caption text-ink-400 mt-2">
              {dash?.totalSkills || 0} distinct skills tracked
            </p>
          </GraphCard>

          <GraphCard title="Skills to grow" description="Connected to what you already know">
            {userId ? (
              dash?.skillsToGrow?.length ? (
                <ul className="space-y-2">
                  {dash.skillsToGrow.map((s: SkillToGrow) => (
                    <li key={s.skill} className="flex items-center justify-between gap-3">
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-er-900 border border-er-500/25 px-2.5 py-1 text-caption font-semibold text-er-200">
                        {s.skill}
                      </span>
                      <span className="text-micro text-ink-400 text-right tabular-nums">
                        via {s.fromSkill} · {s.cooccurrence}×
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-body-sm text-ink-400">
                  Add skills to your profile to unlock suggestions.
                </p>
              )
            ) : (
              <p className="text-body-sm text-ink-400">Sign in to see personalised suggestions.</p>
            )}
          </GraphCard>

          <GraphCard title="Popular pairings" description="Skills that travel together">
            {dash?.topRelated?.length ? (
              <ul className="space-y-2">
                {dash.topRelated.map((p: TopRelated) => (
                  <li
                    key={`${p.skill}-${p.related}`}
                    className="flex items-center justify-between gap-2 rounded-lg bg-white/[0.04] border border-white/10 px-3 py-2"
                  >
                    <span className="text-body-sm text-ink-200 truncate">
                      {p.skill} <span className="text-ink-500 mx-0.5">→</span> {p.related}
                    </span>
                    <span className="text-micro font-semibold text-er-300 tabular-nums flex-shrink-0">
                      {p.count}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-body-sm text-ink-400">No pairs yet.</p>
            )}
          </GraphCard>

          <GraphCard title="By category" description="Where the skills cluster">
            {categories.length ? (
              <CategoryBars data={categories} />
            ) : (
              <p className="text-body-sm text-ink-400">No categories yet.</p>
            )}
          </GraphCard>
        </div>
      )}
    </PageContainer>
  )
}

/** Card wrapper with a consistent heading treatment for the graph panels. */
function GraphCard({
  title,
  description,
  className,
  children,
}: {
  title: string
  description?: string
  className?: string
  children: React.ReactNode
}) {
  return (
    <Card className={cn('p-5', className)}>
      <h3 className="font-display font-semibold text-[16px] text-ink-50 tracking-tight">{title}</h3>
      {description && <p className="text-caption text-ink-400 mt-0.5 mb-3.5">{description}</p>}
      {!description && <div className="mb-3.5" />}
      {children}
    </Card>
  )
}

/** Horizontal bars for the category distribution — a list reads as a ranking. */
function CategoryBars({ data }: { data: [string, number][] }) {
  const max = Math.max(...data.map(([, v]) => v), 1)
  return (
    <div className="space-y-2.5" role="list">
      {data.slice(0, 7).map(([cat, count]) => (
        <div key={cat} role="listitem" className="flex items-center gap-3">
          <span className="text-caption text-ink-300 w-24 truncate flex-shrink-0" title={cat}>{cat}</span>
          <span className="flex-1 h-2 rounded-full bg-er-500/10 overflow-hidden">
            <span
              className="block h-full rounded-full bg-gradient-to-r from-er-500 to-er-600 transition-all duration-500"
              style={{ width: `${Math.max(6, (count / max) * 100)}%` }}
              aria-hidden="true"
            />
          </span>
          <span className="text-caption font-semibold text-ink-50 tabular-nums w-8 text-right flex-shrink-0">
            {count}
          </span>
        </div>
      ))}
    </div>
  )
}
