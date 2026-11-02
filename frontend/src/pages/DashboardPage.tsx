import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { useEffect } from 'react'
import { api } from '../api/client'
import { getName, getId, getRole } from '../utils/identity'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { PageContainer, PageSection } from '../components/ui/PageContainer'
import { StatTile, StatGrid } from '../components/ui/Stat'
import { Reveal } from '../components/ui/Reveal'
import {
  Briefcase, Calendar, Bell, Star, Award, ArrowUpRight, Sparkles, UserPlus,
} from 'lucide-react'

function myId(): number | null {
  const v = getId()
  return v ? Number(v) : null
}

function greeting() {
  const h = new Date().getHours()
  if (h < 5) return 'Up late'
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Good afternoon'
  return 'Good evening'
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const demoUserId = myId()
  const role = getRole()
  const first = (getName() ?? '').split(' ')[0]

  // Alumni and recruiters have their own dashboards with different data.
  // Redirect rather than rendering a student view to the wrong role.
  useEffect(() => {
    if (role === 'alumni') navigate('/dashboard/alumni', { replace: true })
    else if (role === 'recruiter') navigate('/dashboard/recruiter', { replace: true })
  }, [role, navigate])

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
        <div className="rounded-2xl border border-amber-200/70 bg-amber-50/80 px-5 py-4">
          <p className="text-body-sm text-amber-900">
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
  const connections = dash?.connections as { followers: number; following: number } | undefined
  const skillRecs = dash?.skillRecommendations as { recommendations: any[] } | undefined

  const name = identity?.name || getName() || 'Student'
  const recs: any[] = skillRecs?.recommendations ?? []

  return (
    <PageContainer>
      <PageHeader
        title={`${greeting()}${first ? `, ${first}` : ''}`}
        sub="Your projects, invites, and what to learn next"
        actions={
          invites?.count ? <Badge variant="warning">{invites.count} invite{invites.count === 1 ? '' : 's'}</Badge> : undefined
        }
      />

      {/* Identity strip — grounds the numbers in a person. */}
      <Reveal>
        <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white p-6 sm:p-7 mb-5 shadow-card">
          <div className="absolute -top-20 -right-14 h-56 w-56 rounded-full bg-white/10 blur-3xl" aria-hidden="true" />
          <div className="absolute -bottom-24 -left-12 h-48 w-48 rounded-full bg-white/[0.07] blur-3xl" aria-hidden="true" />
          <div className="relative flex items-center gap-4 flex-wrap">
            <span
              className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/15 border border-white/20 font-display font-bold text-xl flex-shrink-0"
              aria-hidden="true"
            >
              {name.charAt(0).toUpperCase()}
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-display font-bold text-[20px] tracking-tight">{name}</p>
              <p className="text-body-sm text-white/70 mt-0.5">
                {identity?.branch}{identity?.year ? ` · Year ${identity.year}` : ''}
              </p>
            </div>
            <div className="flex gap-5 sm:gap-7">
              <div>
                <p className="font-display font-bold text-[24px] leading-none tabular-nums">{connections?.followers ?? 0}</p>
                <p className="text-caption text-white/60 mt-1">Followers</p>
              </div>
              <div>
                <p className="font-display font-bold text-[24px] leading-none tabular-nums">{connections?.following ?? 0}</p>
                <p className="text-caption text-white/60 mt-1">Following</p>
              </div>
            </div>
          </div>
        </section>
      </Reveal>

      <PageSection title="Your activity" className="mb-6">
        <StatGrid>
          <StatTile
            icon={Briefcase}
            tint="brand"
            label="Active projects"
            value={projects?.count ?? 0}
          />
          <StatTile
            icon={Bell}
            tint="amber"
            label="Pending invites"
            value={invites?.count ?? 0}
            hint={invites?.count ? 'Waiting on your reply' : 'Nothing pending'}
          />
          <StatTile
            icon={Calendar}
            tint="sky"
            label="Event RSVPs"
            value={rsvps?.count ?? 0}
          />
          <StatTile
            icon={Award}
            tint="violet"
            label="Peer reviews"
            value={reputation?.reviewCount ?? 0}
            hint={reputation?.averageRating ? `${reputation.averageRating.toFixed(1)}★ average` : 'No rating yet'}
          />
        </StatGrid>
      </PageSection>

      <div className="grid gap-4 lg:grid-cols-3 mb-6">
        <PageSection
          title="My projects"
          action={
            <Link to="/projects" className="inline-flex items-center gap-0.5 text-body-sm font-semibold text-campus-600 hover:text-campus-800">
              All <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          }
        >
          <Card className="p-4 h-full">
            {projects?.items?.length ? (
              <ul className="space-y-2 stagger">
                {projects.items.slice(0, 5).map((p: any, i: number) => (
                  <li
                    key={p.id}
                    style={{ ['--stagger' as string]: Math.min(i, 5) }}
                    className="flex items-center justify-between gap-2 rounded-xl bg-campus-50/60 border border-campus-100/70 px-3 py-2"
                  >
                    <span className="text-body-sm font-medium text-campus-950 truncate">{p.name}</span>
                    <Badge variant={p.status === 'ACTIVE' ? 'success' : 'outline'} size="sm">{p.status}</Badge>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyHint
                icon={Briefcase}
                text="No projects yet"
                cta={{ to: '/projects', label: 'Start one' }}
              />
            )}
          </Card>
        </PageSection>

        <PageSection
          title="Pending invites"
          action={
            <Link to="/projects" className="inline-flex items-center gap-0.5 text-body-sm font-semibold text-campus-600 hover:text-campus-800">
              Review <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          }
        >
          <Card className="p-4 h-full">
            {invites?.items?.length ? (
              <ul className="space-y-2 stagger">
                {invites.items.slice(0, 5).map((i: any, idx: number) => (
                  <li
                    key={i.id}
                    style={{ ['--stagger' as string]: Math.min(idx, 5) }}
                    className="rounded-xl bg-amber-50/70 border border-amber-100 px-3 py-2"
                  >
                    <p className="text-body-sm font-medium text-campus-950 truncate">{i.projectName}</p>
                    <p className="text-caption text-surface-500">from {i.inviterName}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyHint icon={UserPlus} text="No pending invites" />
            )}
          </Card>
        </PageSection>

        <PageSection title="Skills to grow" description="Based on your peers and target roles">
          <Card className="p-4 h-full">
            {recs.length ? (
              <div className="flex flex-wrap gap-1.5">
                {recs.slice(0, 12).map((r: any) => (
                  <span
                    key={r.skill}
                    className="rounded-full bg-accent-50 border border-accent-100 px-2.5 py-1 text-micro font-semibold text-accent-700"
                  >
                    {r.skill}
                  </span>
                ))}
              </div>
            ) : (
              <EmptyHint icon={Star} text="No suggestions yet" />
            )}
          </Card>
        </PageSection>
      </div>

      <Reveal>
        <Link
          to="/chat"
          className="group flex items-center gap-4 rounded-2xl border border-campus-900/[0.07] bg-white shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 p-5"
        >
          <span
            className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white flex-shrink-0"
            aria-hidden="true"
          >
            <Sparkles className="h-5 w-5" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block font-display font-semibold text-[16px] text-campus-950">
              Ask Campus AI anything
            </span>
            <span className="block text-body-sm text-surface-500 mt-0.5">
              Handbooks, calendars, policies — answered with citations
            </span>
          </span>
          <ArrowUpRight className="h-5 w-5 text-surface-300 group-hover:text-campus-600 transition-colors flex-shrink-0" aria-hidden="true" />
        </Link>
      </Reveal>
    </PageContainer>
  )
}

/** Small inline "nothing here yet" used inside the dashboard's summary cards. */
function EmptyHint({
  icon: Icon,
  text,
  cta,
}: {
  icon: typeof Briefcase
  text: string
  cta?: { to: string; label: string }
}) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-6">
      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-campus-50 border border-campus-100 mb-2.5" aria-hidden="true">
        <Icon className="h-4 w-4 text-campus-400" />
      </span>
      <p className="text-body-sm text-surface-400">{text}</p>
      {cta && (
        <Link to={cta.to} className="text-body-sm font-semibold text-campus-600 hover:text-campus-800 mt-1.5">
          {cta.label} →
        </Link>
      )}
    </div>
  )
}
