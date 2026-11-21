import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useSession } from '../utils/identity'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { PageContainer } from '../components/ui/PageContainer'
import { StatTile, StatGrid } from '../components/ui/Stat'
import {
  Briefcase, Calendar, Bell, Award, ArrowUpRight, Sparkles,
} from 'lucide-react'
import { api } from '../api/client'

function greeting() {
  const h = new Date().getHours()
  if (h < 5) return 'Up late'
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Good afternoon'
  return 'Good evening'
}

export default function DashboardPage() {
  const session = useSession()
  const demoUserId = session?.userId ?? null
  const first = (session?.name ?? '').split(' ')[0]

  const { data: dash, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['dashboard', demoUserId],
    queryFn: () => api.get<any>('/dashboard/student'),
    staleTime: 30_000,
    enabled: demoUserId != null,
  })

  if (demoUserId == null) {
    return (
      <PageContainer>
        <PageHeader title="Dashboard" sub="Your Campus Connect overview" />
        <div className="rounded-2xl border border-amber-500/25 bg-amber-500/12 px-5 py-4">
          <p className="text-body-sm text-amber-200">
            No profile selected. Pick one from the sign-in page to see your dashboard.
          </p>
        </div>
      </PageContainer>
    )
  }

  if (isError) {
    return (
      <PageContainer>
        <ErrorState
          title="Could not load dashboard"
          message={(error as Error)?.message || 'An unexpected error occurred'}
          onRetry={() => refetch()}
        />
      </PageContainer>
    )
  }

  if (isLoading || !dash) {
    return (
      <PageContainer>
        <PageHeader title="Dashboard" sub="Your Campus Connect overview" />
        <StatGrid>
          {[0, 1, 2, 3].map(i => <SkeletonCard key={i} />)}
        </StatGrid>
      </PageContainer>
    )
  }

  const identity = dash?.identity as { name: string; branch: string; year: number; availability: string } | undefined
  const projects = dash?.projects as { count: number; items: any[] } | undefined
  const invites = dash?.pendingInvites as { count: number; items: any[] } | undefined
  const rsvps = dash?.events as { count: number; items: any[] } | undefined
  const reputation = dash?.reputation as { reviewCount: number; averageRating: number | null } | undefined

  const name = identity?.name || session?.name || 'Student'
  const projectCount = projects?.count ?? 0
  const inviteCount = invites?.count ?? 0
  const rsvpCount = rsvps?.count ?? 0
  const reviewCount = reputation?.reviewCount ?? 0

  return (
    <PageContainer>
      <PageHeader
        title={`${greeting()}${first ? `, ${first}` : ''}`}
        sub="Your projects, invites, and what to learn next"
      />

      {/* Identity strip */}
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-4 mb-4">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-er-900 border border-white/20 font-display font-bold text-xl">
            {name.charAt(0).toUpperCase()}
          </span>
          <div>
            <p className="font-display font-bold text-[18px] tracking-tight">{name}</p>
            <p className="text-body-sm text-ink-400 mt-0.5">
              {identity?.branch}{identity?.year ? ` · Year ${identity.year}` : ''}
            </p>
          </div>
        </div>
      </div>

      <StatGrid>
        <StatTile
          icon={Briefcase}
          tint="brand"
          label="Active projects"
          value={projectCount}
        />
        <StatTile
          icon={Bell}
          tint="amber"
          label="Pending invites"
          value={inviteCount}
          hint={inviteCount ? 'Waiting on your reply' : 'Nothing pending'}
        />
        <StatTile
          icon={Calendar}
          tint="sky"
          label="Event RSVPs"
          value={rsvpCount}
        />
        <StatTile
          icon={Award}
          tint="teal"
          label="Peer reviews"
          value={reviewCount}
          hint={reputation?.averageRating ? `${reputation.averageRating.toFixed(1)}★ average` : 'No rating yet'}
        />
      </StatGrid>

      <div className="grid gap-4 mb-6">
        <Card className="p-4">
          <h3 className="font-display font-semibold text-[14px] text-ink-50 mb-3">My projects</h3>
          {projectCount > 0 ? (
            <ul className="space-y-2">
              {Array(projectCount).fill(0).map((_, i) => (
                <li key={i} className="flex items-center justify-between gap-2 rounded-xl bg-white/[0.04] border border-white/10 px-3 py-2">
                  <span className="text-body-sm font-medium text-ink-50 truncate">Project {i + 1}</span>
                  <Badge variant="outline" size="sm">Active</Badge>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-body-sm text-ink-400">No projects yet</p>
          )}
        </Card>

        <Card className="p-4">
          <h3 className="font-display font-semibold text-[14px] text-ink-50 mb-3">Pending invites</h3>
          {inviteCount > 0 ? (
            <ul className="space-y-2">
              {Array(inviteCount).fill(0).map((_, i) => (
                <li key={i} className="rounded-xl bg-amber-500/12 border border-amber-500/25 px-3 py-2">
                  <p className="text-body-sm font-medium text-ink-50 truncate">Invite from project {i + 1}</p>
                  <p className="text-caption text-ink-400">awaiting your response</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-body-sm text-ink-400">No pending invites</p>
          )}
        </Card>
      </div>

      <Link
        to="/chat"
        className="inline-flex items-center gap-3 rounded-2xl border border-white/10 bg-er-800 shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 p-4"
      >
        <Sparkles className="h-5 w-5" aria-hidden="true" />
        <span>
          <span className="block font-display font-semibold text-[14px] text-ink-50">Ask Campus AI anything</span>
          <span className="block text-body-sm text-ink-400 mt-0.5">Handbooks, calendars, policies — answered with citations</span>
        </span>
        <ArrowUpRight className="h-4 w-4 text-ink-500 group-hover:text-er-300 transition-colors" aria-hidden="true" />
      </Link>
    </PageContainer>
  )
}