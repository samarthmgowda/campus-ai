import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useEffect } from 'react'
import { api } from '../api/client'
import { getId, getRole } from '../utils/identity'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Briefcase, Calendar, Bell, Star, Award } from 'lucide-react'

function myId(): number | null {
  const v = getId()
  return v ? Number(v) : null
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const demoUserId = myId()
  const role = getRole()

  // Redirect alumni / recruiters to their own dashboard routes
  useEffect(() => {
    if (role === 'alumni') {
      navigate('/dashboard/alumni', { replace: true })
    } else if (role === 'recruiter') {
      navigate('/dashboard/recruiter', { replace: true })
    }
  }, [role, navigate])

  const {
    data: dash,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['dashboard', demoUserId],
    queryFn: () => api.get<any>('/dashboard/student'),
    staleTime: 30_000,
    enabled: demoUserId != null,
  })

  if (demoUserId == null) {
    return (
      <div>
        <PageHeader title="Dashboard" sub="Your Campus Connect overview" />
        <div className="rounded-2xl border border-campus-900/[0.08] bg-amber-50/80 px-5 py-4 mt-4">
          <p className="text-body-sm text-amber-900">
            No demo user selected. Choose a user from the Chat page to view your personalized dashboard.
          </p>
        </div>
      </div>
    )
  }

  if (isError) {
    const errorMessage = (error as any)?.message || 'An unexpected error occurred'
    return <ErrorState title="Could not load dashboard" message={errorMessage} onRetry={() => refetch()} />
  }

  if (isLoading || !dash) {
    return (
      <div>
        <PageHeader title="Dashboard" sub="Your Campus Connect overview" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
          {[1, 2, 3, 4, 5, 6].map(i => <SkeletonCard key={i} />)}
        </div>
      </div>
    )
  }

  const identity = dash?.identity as { name: string; branch: string; year: number; availability: string } | undefined
  const projects = dash?.projects as { count: number; items: any[] } | undefined
  const invites = dash?.pendingInvites as { count: number; items: any[] } | undefined
  const rsvps = dash?.events as { count: number; items: any[] } | undefined
  const reputation = dash?.reputation as { reviewCount: number; averageRating: number | null } | undefined
  // const connections = dash?.connections as { followers: number; following: number } | undefined  // unused
  const skillRecs = dash?.skillRecommendations as { recommendations: any[] } | undefined

  return (
    <div>
      <PageHeader title="Dashboard" sub={`Welcome back, ${identity?.name || 'Student'}`} />

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <Card className="p-4 flex items-center gap-3">
          <Briefcase className="h-8 w-8 text-campus-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{projects?.count || 0}</p>
            <p className="text-sm text-surface-500">Active Projects</p>
          </div>
        </Card>

        <Card className="p-4 flex items-center gap-3">
          <Bell className="h-8 w-8 text-amber-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{invites?.count || 0}</p>
            <p className="text-sm text-surface-500">Pending Invites</p>
          </div>
        </Card>

        <Card className="p-4 flex items-center gap-3">
          <Calendar className="h-8 w-8 text-blue-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{rsvps?.count || 0}</p>
            <p className="text-sm text-surface-500">Event RSVPs</p>
          </div>
        </Card>

        <Card className="p-4 flex items-center gap-3">
          <Award className="h-8 w-8 text-purple-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{reputation?.reviewCount || 0}</p>
            <p className="text-sm text-surface-500">Reviews</p>
            {reputation?.averageRating && (
              <p className="text-xs text-surface-400">{reputation.averageRating.toFixed(1)}★ avg</p>
            )}
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* My Projects */}
        <Card className="p-5">
          <h3 className="font-semibold text-surface-900 mb-3 flex items-center gap-2">
            <Briefcase className="h-4 w-4" /> My Projects
          </h3>
          {projects?.items && projects.items.length > 0 ? (
            <div className="space-y-2">
              {projects.items.slice(0, 5).map((p: any) => (
                <div key={p.id} className="text-sm">
                  <span className="font-medium">{p.name}</span>
                  <Badge variant="outline" className="ml-1 text-xs">{p.status}</Badge>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-surface-400">No active projects yet.</p>
          )}
        </Card>

        {/* Pending Invites */}
        <Card className="p-5">
          <h3 className="font-semibold text-surface-900 mb-3 flex items-center gap-2">
            <Bell className="h-4 w-4" /> Pending Invites
          </h3>
          {invites?.items && invites.items.length > 0 ? (
            <div className="space-y-2">
              {invites.items.slice(0, 5).map((i: any) => (
                <div key={i.id} className="text-sm">
                  <span className="font-medium">{i.projectName}</span>
                  <p className="text-xs text-surface-500">from {i.inviterName}</p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-surface-400">No pending invites.</p>
          )}
        </Card>

        {/* Skill Recommendations */}
        <Card className="p-5">
          <h3 className="font-semibold text-surface-900 mb-3 flex items-center gap-2">
            <Star className="h-4 w-4" /> Skills to Grow
          </h3>
          {skillRecs?.recommendations && skillRecs.recommendations.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {skillRecs.recommendations.slice(0, 10).map((r: any) => (
                <Badge key={r.skill} variant="outline">
                  {r.skill}
                </Badge>
              ))}
            </div>
          ) : (
            <p className="text-sm text-surface-400">No recommendations yet.</p>
          )}
        </Card>
      </div>
    </div>
  )
}
