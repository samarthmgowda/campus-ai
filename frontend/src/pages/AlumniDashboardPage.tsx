import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PageHeader, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { GraduationCap, Briefcase, Bell, Users } from 'lucide-react'

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

function myId(): number | null {
  const v = localStorage.getItem('demoUserId')
  return v ? Number(v) : null
}

export default function AlumniDashboardPage() {
  const userId = myId()

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
      <div>
        <PageHeader title="Alumni Dashboard" sub="Alumni overview" />
        <div className="rounded-2xl border border-amber-200/70 bg-amber-50/80 px-5 py-4 mt-4">
          <p className="text-body-sm text-amber-900">
            No demo user selected. Choose a user from the login page.
          </p>
        </div>
      </div>
    )
  }

  if (isError) {
    return (
      <ErrorState
        title="Could not load alumni dashboard"
        message={(error as Error)?.message || 'An unexpected error occurred'}
        onRetry={() => refetch()}
      />
    )
  }

  if (isLoading || !dash) {
    return (
      <div>
        <PageHeader title="Alumni Dashboard" sub="Alumni overview" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
          {[1, 2, 3, 4].map(i => <SkeletonCard key={i} />)}
        </div>
      </div>
    )
  }

  const identity = dash.identity as Identity | undefined
  const mentorship = dash.mentorshipRequests as { count: number; items: MentorshipRequest[] } | undefined
  const referrals = dash.referralRequests as { count: number; items: ReferralRequest[] } | undefined
  const connections = dash.connections as Connections | undefined

  return (
    <div>
      <PageHeader
        title="Alumni Dashboard"
        sub={`Welcome back, ${identity?.name || 'Alumni'}`}
      />

      {/* Identity card */}
      <Card className="p-5 mb-6">
        <div className="flex items-center gap-4">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-[#667eea] to-[#764ba2] font-display font-bold text-xl text-white flex-shrink-0" aria-hidden="true">
            {identity?.name?.charAt(0).toUpperCase() || '?'}
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-display font-bold text-[20px] text-campus-950">{identity?.name || '—'}</p>
            <p className="text-body-sm text-surface-500 mt-0.5">
              {identity?.currentRole} · {identity?.currentCompany}
            </p>
            <p className="text-caption text-surface-400 mt-0.5">
              {identity?.branch} · Grad {identity?.graduationYear}
            </p>
          </div>
          {identity?.availability && (
            <Badge variant={identity.availability === 'UNAVAILABLE' ? 'default' : 'success'} size="sm">
              {identity.availability}
            </Badge>
          )}
        </div>
      </Card>

      {/* Quick stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
        <Card className="p-4 flex items-center gap-3">
          <GraduationCap className="h-7 w-7 text-campus-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{mentorship?.count || 0}</p>
            <p className="text-sm text-surface-500">Mentorship Requests</p>
          </div>
        </Card>
        <Card className="p-4 flex items-center gap-3">
          <Briefcase className="h-7 w-7 text-purple-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{referrals?.count || 0}</p>
            <p className="text-sm text-surface-500">Referral Requests</p>
          </div>
        </Card>
        <Card className="p-4 flex items-center gap-3">
          <Users className="h-7 w-7 text-blue-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{connections?.followers || 0}</p>
            <p className="text-sm text-surface-500">Followers</p>
          </div>
        </Card>
        <Card className="p-4 flex items-center gap-3">
          <Bell className="h-7 w-7 text-amber-500" />
          <div>
            <p className="text-2xl font-bold text-surface-900">{dash.unreadNotifications ?? 0}</p>
            <p className="text-sm text-surface-500">Unread Notifications</p>
          </div>
        </Card>
      </div>

      {/* Detailed lists */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Pending mentorship requests */}
        <Card className="p-5">
          <h3 className="font-semibold text-surface-900 mb-3 flex items-center gap-2">
            <GraduationCap className="h-4 w-4" /> Pending Mentorship
          </h3>
          {mentorship && mentorship.items && mentorship.items.length > 0 ? (
            <div className="space-y-3">
              {mentorship.items.map((r: MentorshipRequest) => (
                <div key={r.id} className="rounded-xl bg-surface-50/70 border border-surface-200/70 px-3.5 py-3">
                  <p className="font-medium text-surface-900 text-body-sm">{r.studentName}</p>
                  <p className="text-caption text-surface-500 mt-1 line-clamp-2">{r.message}</p>
                  <Badge variant="outline" size="sm" className="mt-2">{r.status}</Badge>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-body-sm text-surface-400">No pending mentorship requests.</p>
          )}
        </Card>

        {/* Pending referral requests */}
        <Card className="p-5">
          <h3 className="font-semibold text-surface-900 mb-3 flex items-center gap-2">
            <Briefcase className="h-4 w-4" /> Pending Referrals
          </h3>
          {referrals && referrals.items && referrals.items.length > 0 ? (
            <div className="space-y-3">
              {referrals.items.map((r: ReferralRequest) => (
                <div key={r.id} className="rounded-xl bg-surface-50/70 border border-surface-200/70 px-3.5 py-3">
                  <p className="font-medium text-surface-900 text-body-sm">{r.studentName}</p>
                  <p className="text-caption text-surface-500 mt-1">{r.company} · {r.role}</p>
                  <Badge variant="outline" size="sm" className="mt-2">{r.status}</Badge>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-body-sm text-surface-400">No pending referral requests.</p>
          )}
        </Card>
      </div>
    </div>
  )
}
