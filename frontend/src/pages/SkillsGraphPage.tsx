// import { useState } from 'react'  // unused
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { getId } from '../utils/identity'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'

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
      <ErrorState
        title="Could not load skill graph"
        message="The skill graph data could not be loaded."
        onRetry={() => refetch()}
      />
    )
  }

  return (
    <div>
      <PageHeader
        title="Skill Graph"
        sub="Explore how skills connect across campus and discover what to learn next"
      />

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4, 5, 6].map(i => (
            <SkeletonCard key={i} />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {/* Top skills */}
          <Card className="p-5">
            <h3 className="text-lg font-semibold text-surface-900 mb-3">Top Skills on Campus</h3>
            <div className="flex flex-wrap gap-2">
              {dash?.topSkills?.map((s: TopSkill) => (
                <div key={s.skill} className="flex items-center gap-1.5">
                  <Badge variant="outline">{s.skill}</Badge>
                  <span className="text-xs text-surface-500">{s.count}</span>
                </div>
              )) || []}
            </div>
          </Card>

          {/* Skills to grow */}
          <Card className="p-5">
            <h3 className="text-lg font-semibold text-surface-900 mb-3">Skills to Grow</h3>
            {userId ? (
              dash?.skillsToGrow && dash.skillsToGrow.length > 0 ? (
                <div className="space-y-2">
                  {dash.skillsToGrow.map((s: SkillToGrow) => (
                    <div key={s.skill} className="flex items-center justify-between">
                      <Badge variant="outline">{s.skill}</Badge>
                      <span className="text-xs text-surface-500">
                        connected to {s.fromSkill} ({s.cooccurrence}×)
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-surface-500">No personalized suggestions. Add skills to your profile to see recommendations.</p>
              )
            ) : (
              <p className="text-sm text-surface-500">Sign in to see skills to grow based on your profile.</p>
            )}
          </Card>

          {/* Top related pairs */}
          <Card className="p-5">
            <h3 className="text-lg font-semibold text-surface-900 mb-3">Popular Skill Pairs</h3>
            <div className="space-y-2">
              {dash?.topRelated?.map((p: TopRelated) => (
                <div key={`${p.skill}-${p.related}`} className="flex items-center justify-between text-sm">
                  <span>{p.skill} → {p.related}</span>
                  <Badge variant="outline">{p.count}</Badge>
                </div>
              )) || []}
            </div>
          </Card>

          {/* Category distribution */}
          <Card className="p-5">
            <h3 className="text-lg font-semibold text-surface-900 mb-3">By Category</h3>
            <div className="space-y-2">
              {dash?.categoryDistribution && Object.entries(dash.categoryDistribution)
                .filter(([_, v]) => v > 0)
                .sort(([, a], [, b]) => b - a)
                .map(([cat, count]) => (
                  <div key={cat} className="flex items-center justify-between">
                    <span className="text-sm text-surface-700">{cat}</span>
                    <Badge variant="outline">{count}</Badge>
                  </div>
                ))
              }
            </div>
          </Card>

          {/* Stats */}
          <Card className="p-5">
            <h3 className="text-lg font-semibold text-surface-900 mb-3">Stats</h3>
            <div className="space-y-2 text-2xl font-medium text-campus-600">
              <p>{dash?.totalSkills || 0} skills</p>
              <p className="text-base text-surface-500">tracked across</p>
              <p>{dash?.totalProfiles || 0} profiles</p>
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}
