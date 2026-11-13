import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { useSession } from '../utils/identity'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { PageContainer, PageSection } from '../components/ui/PageContainer'
import { StatTile, StatGrid } from '../components/ui/Stat'
import { Reveal } from '../components/ui/Reveal'
import { GraduationCap, Briefcase, Bell, Users, ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'

interface Identity {
  name: string
  branch: string
  graduationYear: number
  currentRole: string
  currentCompany: string
  availability: string
}

interface MentorshipRequest {
  id: number
  studentName: string
  message: string
  status: string
  createdAt: string
}

interface ReferralRequest {
  id: number
  studentName: string
  company: string
  role: string
  status: string
  createdAt: string
}

interface Connections {
  followers: number
  following: number
}

export default function AlumniDashboardPage() {
  // Reactive so a switch cannot leave the previous alumni's numbers on screen.
  const userId = useSession()?.userId ?? null

  const {
    data: dash,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['alumni-dashboard', userId],
    queryFn: () => api.get<any>(`/dashboard/alumni?alumniId=${userId}`),
    staleTime: 30_000,
    enabled: userId != null,
  })

  if (userId == null) {
    return (
      <PageContainer>
        <PageHeader title="Dashboard" sub="Alumni overview" />
        <div className="rounded-2xl border border-amber-200/70 bg-amber-50/80 px-5 py-4">
          <p className="text-body-sm text-amber-900">
            No demo user selected. Choose a user from the login page.
          </p>
        </div>
      </PageContainer>
    )
  }

  if (isError) {
    return (
      <PageContainer>
        <ErrorState
          title="Could not load alumni dashboard"
          message={(error as Error)?.message || 'An unexpected error occurred'}
          onRetry={() => refetch()}
        />
      </PageContainer>
    )
  }

  if (isLoading || !dash) {
    return (
      <PageContainer>
        <PageHeader title="Dashboard" sub="Alumni overview" />
        <StatGrid>
          {[0, 1, 2, 3].map(i => <SkeletonCard key={i} />)}
        </StatGrid>
      </PageContainer>
    )
  }

  const identity = dash.identity as Identity | undefined
  const mentorship = dash.mentorshipRequests as { count: number; items: MentorshipRequest[] } | undefined
  const referrals = dash.referralRequests as { count: number; items: ReferralRequest[] } | undefined
  const connections = dash.connections as Connections | undefined
  const pendingMentorship = (mentorship?.items ?? []).filter(r => r.status === 'PENDING').length

  return (
    <PageContainer>
      <PageHeader
        title={`Welcome back, ${identity?.name?.split(' ')[0] || 'Alumni'}`}
        sub="Everyone waiting on your guidance, in one place"
        actions={
          pendingMentorship > 0 ? (
            <Badge variant="accent">{pendingMentorship} awaiting reply</Badge>
          ) : (
            <Badge variant="success">All caught up</Badge>
          )
        }
      />

      {/* Identity banner — carries the person, not just a number. */}
      <Reveal>
        <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white p-6 sm:p-7 mb-5 shadow-card">
          <div className="absolute -top-20 -right-14 h-56 w-56 rounded-full bg-white/10 blur-3xl" aria-hidden="true" />
          <div className="relative flex items-center gap-4 flex-wrap">
            <span
              className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/15 border border-white/20 font-display font-bold text-xl flex-shrink-0"
              aria-hidden="true"
            >
              {identity?.name?.charAt(0).toUpperCase() || '?'}
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-display font-bold text-[20px] tracking-tight">{identity?.name || '—'}</p>
              <p className="text-body-sm text-white/70 mt-0.5">
                {identity?.currentRole}{identity?.currentCompany ? ` · ${identity.currentCompany}` : ''}
              </p>
              <p className="text-caption text-white/50 mt-0.5">
                {identity?.branch} · Graduated {identity?.graduationYear}
              </p>
            </div>
            {identity?.availability && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 border border-white/15 px-3 py-1.5 text-caption font-semibold">
                <span
                  className={`h-1.5 w-1.5 rounded-full ${identity.availability === 'UNAVAILABLE' ? 'bg-white/40' : 'bg-emerald-300'}`}
                  aria-hidden="true"
                />
                {identity.availability === 'UNAVAILABLE' ? 'Not taking requests' : 'Open to requests'}
              </span>
            )}
          </div>
        </section>
      </Reveal>

      <PageSection title="Your activity" className="mb-6">
        <StatGrid>
          <StatTile
            icon={GraduationCap}
            tint="brand"
            label="Mentorship requests"
            value={mentorship?.count ?? 0}
            hint={pendingMentorship > 0 ? `${pendingMentorship} pending` : 'None pending'}
          />
          <StatTile
            icon={Briefcase}
            tint="accent"
            label="Referral requests"
            value={referrals?.count ?? 0}
            hint="Students hoping for a warm intro"
          />
          <StatTile
            icon={Users}
            tint="sky"
            label="Followers"
            value={connections?.followers ?? 0}
            hint={`Following ${connections?.following ?? 0}`}
          />
          <StatTile
            icon={Bell}
            tint="amber"
            label="Unread notifications"
            value={dash.unreadNotifications ?? 0}
          />
        </StatGrid>
      </PageSection>

      <div className="grid gap-4 lg:grid-cols-2">
        <PageSection
          title="Mentorship requests"
          description="Students who asked you for guidance"
          action={
            <Link
              to="/mentorship"
              className="inline-flex items-center gap-0.5 text-body-sm font-semibold text-campus-600 hover:text-campus-800"
            >
              Manage <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          }
        >
          <Card className="p-4 h-full">
            {mentorship?.items?.length ? (
              <div className="space-y-2.5 stagger">
                {mentorship.items.map((r, i) => (
                  <div
                    key={r.id}
                    style={{ ['--stagger' as string]: Math.min(i, 6) }}
                    className="rounded-xl border border-campus-100 bg-campus-50/60 px-3.5 py-3"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <p className="font-semibold text-body-sm text-campus-950">{r.studentName}</p>
                      <Badge variant={r.status === 'PENDING' ? 'warning' : 'outline'} size="sm">
                        {r.status}
                      </Badge>
                    </div>
                    {r.message && (
                      <p className="text-caption text-surface-500 mt-1 line-clamp-2 leading-relaxed">{r.message}</p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-body-sm text-surface-400 text-center py-6">No mentorship requests yet.</p>
            )}
          </Card>
        </PageSection>

        <PageSection
          title="Referral requests"
          description="Warm intros students are hoping for"
          action={
            <Link
              to="/referrals"
              className="inline-flex items-center gap-0.5 text-body-sm font-semibold text-campus-600 hover:text-campus-800"
            >
              Manage <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          }
        >
          <Card className="p-4 h-full">
            {referrals?.items?.length ? (
              <div className="space-y-2.5 stagger">
                {referrals.items.map((r, i) => (
                  <div
                    key={r.id}
                    style={{ ['--stagger' as string]: Math.min(i, 6) }}
                    className="rounded-xl border border-accent-100 bg-accent-50/50 px-3.5 py-3"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <p className="font-semibold text-body-sm text-campus-950">{r.studentName}</p>
                      <Badge variant={r.status === 'PENDING' ? 'warning' : 'outline'} size="sm">
                        {r.status}
                      </Badge>
                    </div>
                    <p className="text-caption text-surface-500 mt-1">
                      {r.role} at {r.company}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-body-sm text-surface-400 text-center py-6">No referral requests yet.</p>
            )}
          </Card>
        </PageSection>
      </div>
    </PageContainer>
  )
}
