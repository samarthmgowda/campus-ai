import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { getId } from '../utils/identity'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Briefcase, Users, Bell, FileText } from 'lucide-react'

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
      <div>
        <PageHeader title="Recruiter Dashboard" sub="Recruiter overview" />
        <div className="rounded-2xl border border-amber-200/70 bg-amber-50/80 px-5 py-4 mt-4">
          <p className="text-body-sm text-amber-900">
            No demo user selected. Choose a recruiter from the login page.
          </p>
        </div>
      </div>
    )
  }

  if (isError) {
    return (
      <ErrorState
        title="Could not load recruiter dashboard"
        message={(error as Error)?.message || 'An unexpected error occurred'}
        onRetry={() => refetch()}
      />
    )
  }

  if (isLoading || !dash) {
    return (
      <div>
        <PageHeader title="Recruiter Dashboard" sub="Recruiter overview" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
          {[1, 2, 3, 4].map(i => <SkeletonCard key={i} />)}
        </div>
      </div>
    )
  }

  const identity = dash.identity as { role: string; userId: number } | undefined
  const postings = dash.postings as { count: number; items: RecruiterPosting[] } | undefined

  const openCount = postings?.items?.filter(p => p.status === 'OPEN').length || 0
  const closedCount = postings?.items?.filter(p => p.status === 'CLOSED').length || 0
  const totalApplicants = postings?.items?.reduce((sum, p) => sum + (p.applicantCount || 0), 0) || 0

  return (
    <div>
      <PageHeader
        title="Recruiter Dashboard"
        sub={`Welcome back, ${dash.recruiterName || identity?.role || 'Recruiter'}`}
      />

      {/* Quick stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
        <Card className="p-4 flex items-center gap-3">
          <FileText className="h-7 w-7 text-campus-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{postings?.count || 0}</p>
            <p className="text-sm text-surface-500">Total Postings</p>
          </div>
        </Card>
        <Card className="p-4 flex items-center gap-3">
          <FileText className="h-7 w-7 text-green-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{openCount}</p>
            <p className="text-sm text-surface-500">Open</p>
          </div>
        </Card>
        <Card className="p-4 flex items-center gap-3">
          <FileText className="h-7 w-7 text-amber-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{closedCount}</p>
            <p className="text-sm text-surface-500">Closed</p>
          </div>
        </Card>
        <Card className="p-4 flex items-center gap-3">
          <Users className="h-7 w-7 text-purple-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{totalApplicants}</p>
            <p className="text-sm text-surface-500">Total Applicants</p>
          </div>
        </Card>
      </div>

      {/* Unread notifications */}
      <Card className="p-4 flex items-center gap-3 mb-6">
        <Bell className="h-6 w-6 text-amber-500" />
        <div>
          <p className="font-semibold text-surface-900">Unread Notifications</p>
          <p className="text-sm text-surface-500">{dash.unreadNotifications ?? 0} new items</p>
        </div>
      </Card>

      {/* Postings list */}
      <Card className="p-5">
        <h3 className="font-semibold text-surface-900 mb-3 flex items-center gap-2">
          <Briefcase className="h-4 w-4" /> Your Postings
        </h3>
        {postings && postings.items && postings.items.length > 0 ? (
          <div className="space-y-3">
            {postings.items.map((p: RecruiterPosting) => (
              <div key={p.id} className="rounded-xl border border-surface-200/70 px-4 py-3 hover:bg-surface-50/50">
                <div className="flex items-center justify-between">
                  <p className="font-medium text-surface-900">{p.title}</p>
                  <Badge variant={p.status === 'OPEN' ? 'success' : 'default'} size="sm">
                    {p.status}
                  </Badge>
                </div>
                <p className="text-caption text-surface-500 mt-1 line-clamp-2">{p.description}</p>
                {p.requiredSkills && (
                  <p className="text-caption text-surface-400 mt-1">Skills: {p.requiredSkills}</p>
                )}
                <div className="flex items-center gap-4 mt-2 text-caption text-surface-400">
                  <span className="flex items-center gap-1">
                    <Users className="h-3 w-3" /> {p.applicantCount || 0} applicants
                  </span>
                  <span>Posted {new Date(p.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-body-sm text-surface-400">No postings yet. Create one to get started.</p>
        )}
      </Card>
    </div>
  )
}
