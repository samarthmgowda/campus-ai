import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { getId } from '../utils/identity'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { PageContainer, PageSection } from '../components/ui/PageContainer'
import { StatTile, StatGrid } from '../components/ui/Stat'
import { Reveal } from '../components/ui/Reveal'
import { Briefcase, Users, Bell, FileText, ArrowUpRight, Clock } from 'lucide-react'

interface RecruiterPosting {
  id: number
  title: string
  description: string
  requiredSkills: string
  status: 'OPEN' | 'CLOSED'
  createdAt: string
  applicantCount: number
}

function myId(): number | null {
  const v = getId()
  return v ? Number(v) : null
}

export default function RecruiterDashboardPage() {
  const userId = myId()

  const {
    data: dash,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['recruiter-dashboard', userId],
    queryFn: () => api.get<any>('/dashboard/recruiter'),
    staleTime: 30_000,
    enabled: userId != null,
  })

  if (userId == null) {
    return (
      <PageContainer>
        <PageHeader title="Dashboard" sub="Recruiter overview" />
        <div className="rounded-2xl border border-amber-200/70 bg-amber-50/80 px-5 py-4">
          <p className="text-body-sm text-amber-900">
            No demo user selected. Choose a recruiter from the login page.
          </p>
        </div>
      </PageContainer>
    )
  }

  if (isError) {
    return (
      <PageContainer>
        <ErrorState
          title="Could not load recruiter dashboard"
          message={(error as Error)?.message || 'An unexpected error occurred'}
          onRetry={() => refetch()}
        />
      </PageContainer>
    )
  }

  if (isLoading || !dash) {
    return (
      <PageContainer>
        <PageHeader title="Dashboard" sub="Recruiter overview" />
        <StatGrid>
          {[0, 1, 2, 3].map(i => <SkeletonCard key={i} />)}
        </StatGrid>
      </PageContainer>
    )
  }

  const rawName = (dash.recruiterName ?? '') as string
  const identity = dash.identity as { role: string; userId: number } | undefined
  const postings = dash.postings as { count: number; items: RecruiterPosting[] } | undefined
  const unread = (dash.unreadNotifications ?? 0) as number

  const openCount = postings?.items?.filter(p => p.status === 'OPEN').length || 0
  const closedCount = postings?.items?.filter(p => p.status === 'CLOSED').length || 0
  const totalApplicants = postings?.items?.reduce((sum, p) => sum + (p.applicantCount || 0), 0) || 0
  const greetingName = rawName.split(' ')[0] || identity?.role || 'Recruiter'

  return (
    <PageContainer>
      <PageHeader
        title={`Welcome back, ${greetingName}`}
        sub="Your pipeline at a glance — postings, applicants, and what needs a reply"
        actions={
          <Link
            to="/recruiter"
            className="inline-flex items-center gap-1.5 rounded-xl bg-campus-950 text-white px-4 py-2.5 text-body-sm font-semibold hover:bg-campus-900 transition-colors"
          >
            Manage careers <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
          </Link>
        }
      />

      <PageSection title="Pipeline" className="mb-6">
        <StatGrid>
          <StatTile icon={FileText} tint="brand" label="Total postings" value={postings?.count ?? 0} />
          <StatTile
            icon={Briefcase}
            tint="emerald"
            label="Open roles"
            value={openCount}
            hint={closedCount > 0 ? `${closedCount} closed` : 'All active'}
          />
          <StatTile icon={Users} tint="violet" label="Total applicants" value={totalApplicants} />
          <StatTile
            icon={Bell}
            tint="amber"
            label="Unread notifications"
            value={unread}
            hint={unread > 0 ? 'Something needs you' : 'Nothing new'}
          />
        </StatGrid>
      </PageSection>

      <Reveal>
        <PageSection title="Your postings" description="Every role you have listed, newest first">
          <Card className="p-0 overflow-hidden">
            {postings?.items?.length ? (
              <ul className="divide-y divide-campus-900/[0.05]">
                {postings.items.map((p, i) => (
                  <li
                    key={p.id}
                    style={{ ['--stagger' as string]: Math.min(i, 8) }}
                    className="px-5 py-4 hover:bg-campus-50/40 transition-colors"
                  >
                    <div className="flex items-start justify-between gap-3 flex-wrap">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="font-display font-semibold text-[15px] text-campus-950 tracking-tight">
                            {p.title}
                          </p>
                          <Badge variant={p.status === 'OPEN' ? 'success' : 'outline'} size="sm">
                            {p.status}
                          </Badge>
                        </div>
                        {p.description && (
                          <p className="text-body-sm text-surface-500 mt-1 line-clamp-2 leading-relaxed">
                            {p.description}
                          </p>
                        )}
                        {p.requiredSkills && (
                          <div className="flex flex-wrap gap-1.5 mt-2">
                            {p.requiredSkills.split(',').map(s => s.trim()).filter(Boolean).slice(0, 5).map(s => (
                              <span
                                key={s}
                                className="rounded-full bg-campus-50 border border-campus-100 px-2.5 py-0.5 text-micro font-medium text-campus-700"
                              >
                                {s}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                      <div className="text-right flex-shrink-0">
                        <p className="font-display font-bold text-[22px] leading-none tabular-nums text-campus-950">
                          {p.applicantCount || 0}
                        </p>
                        <p className="text-micro text-surface-400 mt-1">
                          applicant{p.applicantCount === 1 ? '' : 's'}
                        </p>
                        <p className="text-micro text-surface-300 mt-2 inline-flex items-center gap-1 tabular-nums">
                          <Clock className="h-3 w-3" aria-hidden="true" />
                          {new Date(p.createdAt).toLocaleDateString()}
                        </p>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="px-5 py-12 text-center">
                <p className="text-body-sm text-surface-400">No postings yet.</p>
                <Link
                  to="/recruiter"
                  className="mt-3 inline-flex items-center gap-1 text-body-sm font-semibold text-campus-600 hover:text-campus-800"
                >
                  Create your first posting <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              </div>
            )}
          </Card>
        </PageSection>
      </Reveal>
    </PageContainer>
  )
}
