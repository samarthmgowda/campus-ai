import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Alumni } from '../types'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Briefcase, Send, Undo2, Check, X, Search, Building2 } from 'lucide-react'
import { cn } from '../utils/cn'

interface ReferralDto {
  id: number
  studentId: number | null
  studentName: string | null
  alumniId: number | null
  alumniName: string | null
  status: string
  message: string | null
  company: string | null
  role: string | null
  createdAt: string | null
}

const STATUS_VARIANT: Record<string, 'default' | 'success' | 'danger' | 'warning' | 'outline'> = {
  PENDING: 'warning',
  ACCEPTED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'outline',
  CANCELLED: 'outline',
}

function isReferralAvailable(a: Alumni): boolean {
  const v = (a.availability ?? '').toUpperCase()
  return v.includes('REFERRAL') || v === 'BOTH'
}

export default function ReferralPage() {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState('')
  const [skillFilter, setSkillFilter] = useState('')
  const [requestFor, setRequestFor] = useState<number | null>(null)
  const [requestMsg, setRequestMsg] = useState('')
  const [company, setCompany] = useState('')
  const [role, setRole] = useState('')

  const { data: myRequests = [], isLoading, error, refetch } = useQuery({
    queryKey: ['referrals-my', statusFilter],
    queryFn: () => api.get<ReferralDto[]>(`/referrals/my${statusFilter ? `?status=${statusFilter}` : ''}`),
  })

  const { data: alumniAll = [], isLoading: alumniLoading } = useQuery({
    queryKey: ['alumni-dir-all'],
    queryFn: () => api.get<Alumni[]>('/alumni'),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['referrals-my'] })
  }

  const requestMutation = useMutation({
    mutationFn: ({ alumniId, message, company, role }: { alumniId: number; message: string; company: string; role: string }) =>
      api.post<ReferralDto>('/referrals/request', { alumniId, message, company: company || undefined, role: role || undefined }),
    onSuccess: () => { setRequestFor(null); setRequestMsg(''); setCompany(''); setRole(''); invalidate() },
  })

  const withdrawMutation = useMutation({
    mutationFn: (id: number) => api.post(`/referrals/${id}/withdraw`, {}),
    onSuccess: () => invalidate(),
  })

  const respondMutation = useMutation({
    mutationFn: ({ id, action }: { id: number; action: string }) =>
      api.post<ReferralDto>(`/referrals/${id}/respond`, { action }),
    onSuccess: () => invalidate(),
  })

  const skillQ = skillFilter.trim().toLowerCase()
  const alumni = alumniAll
    .filter(a => isReferralAvailable(a))
    .filter(a => !skillQ || (a.skills ?? '').toLowerCase().includes(skillQ) || a.name.toLowerCase().includes(skillQ))

  const pendingCount = myRequests.filter(r => r.status === 'PENDING').length

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="Referrals"
        sub={myRequests.length > 0 ? `${pendingCount} awaiting reply · ${myRequests.length} total requests` : 'Job referrals from alumni who opt in'}
      />

      <section aria-label="My referral requests" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden mb-6">
        <div className="px-5 pt-5 pb-4 border-b border-campus-900/[0.06] bg-campus-50/50">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div>
              <p className="font-display font-semibold text-[16px] text-campus-950">My requests</p>
              <p className="text-caption text-surface-400 mt-0.5">Pending can be withdrawn · accepted or rejected are final</p>
            </div>
            <div className="flex gap-1.5" role="group" aria-label="Filter by status">
              {['', 'PENDING', 'ACCEPTED', 'REJECTED'].map(s => (
                <button
                  key={s || 'all'}
                  onClick={() => setStatusFilter(s)}
                  aria-pressed={statusFilter === s}
                  className={cn(
                    'rounded-lg px-2.5 py-1.5 text-body-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-campus-500',
                    statusFilter === s ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white' : 'text-surface-400 hover:text-campus-900 hover:bg-campus-50'
                  )}
                >
                  {s || 'All'}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div className="p-4 sm:p-5">
          {isLoading ? (
            <div className="space-y-3"><SkeletonCard /><SkeletonCard /></div>
          ) : error ? (
            <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
          ) : myRequests.length === 0 ? (
            <EmptyState
              icon={Briefcase}
              title={statusFilter ? `No ${statusFilter.toLowerCase()} requests` : 'No referral requests yet'}
              hint="Find an alumni below who is open to referrals — mention the company and role."
            />
          ) : (
            <div className="space-y-2.5 stagger">
              {myRequests.map((r, i) => (
                <div
                  key={r.id}
                  style={{ ['--stagger' as string]: Math.min(i, 8) }}
                  className="flex items-center gap-3 rounded-xl border border-campus-900/[0.06] bg-white hover:border-campus-900/15 hover:shadow-card transition-all px-4 py-3"
                >
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold text-body-sm flex-shrink-0" aria-hidden="true">
                    {(r.alumniName ?? '?').charAt(0).toUpperCase()}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="font-semibold text-body-sm text-campus-950">{r.alumniName ?? `Alumni #${r.alumniId}`}</p>
                      <Badge variant={STATUS_VARIANT[r.status] ?? 'default'} size="sm">{r.status}</Badge>
                    </div>
                    <p className="text-caption text-surface-500 mt-0.5 truncate">
                      {[r.company, r.role].filter(Boolean).join(' · ') || 'General referral'}
                    </p>
                    {r.message && <p className="text-body-sm text-surface-500 truncate mt-0.5">“{r.message}”</p>}
                  </div>
                  {r.status === 'PENDING' && (
                    <div className="flex gap-1 flex-shrink-0">
                      <button
                        onClick={() => respondMutation.mutate({ id: r.id, action: 'ACCEPT' })}
                        aria-label="Accept (demo student-side)"
                        title="Accept (demo)"
                        className="p-2 rounded-lg text-emerald-600 hover:bg-emerald-50 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                      >
                        <Check className="h-4 w-4" />
                      </button>
                      <button
                        onClick={() => withdrawMutation.mutate(r.id)}
                        aria-label={`Withdraw request to ${r.alumniName}`}
                        title="Withdraw"
                        className="p-2 rounded-lg text-surface-300 hover:text-surface-600 hover:bg-surface-100 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                      >
                        <Undo2 className="h-4 w-4" />
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
          {(withdrawMutation.isError || respondMutation.isError || requestMutation.isError) && (
            <p className="text-body-sm text-red-600 mt-3" role="alert">
              {((withdrawMutation.error ?? respondMutation.error ?? requestMutation.error) as Error)?.message}
            </p>
          )}
        </div>
      </section>

      <section aria-label="Find a referral">
        <div className="flex items-center justify-between flex-wrap gap-3 mb-4">
          <div>
            <p className="font-display font-semibold text-[16px] text-campus-950">Find a referral</p>
            <p className="text-caption text-surface-400 mt-0.5">Only alumni open to referrals · {alumni.length} available</p>
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-surface-400 pointer-events-none" aria-hidden="true" />
            <input
              value={skillFilter}
              onChange={e => setSkillFilter(e.target.value)}
              placeholder="Filter by skill or name"
              aria-label="Filter alumni by skill"
              className="bg-white border border-campus-900/10 rounded-xl pl-9 pr-3 py-2 text-body-sm shadow-card placeholder:text-surface-400 focus:outline-none focus:ring-2 focus:ring-campus-500 w-[220px]"
            />
          </div>
        </div>
        {alumniLoading ? (
          <div className="grid gap-3 sm:grid-cols-2"><SkeletonCard /><SkeletonCard /></div>
        ) : alumni.length === 0 ? (
          <EmptyState
            icon={Building2}
            title="No alumni open to referrals"
            hint="Nobody with REFERRALS availability matches — try clearing the filter."
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 stagger">
            {alumni.slice(0, 12).map((a, i) => {
              const skillList = (a.skills ?? '').split(',').map(s => s.trim()).filter(Boolean)
              return (
                <article
                  key={a.id}
                  style={{ ['--stagger' as string]: Math.min(i, 8) }}
                  className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 p-5"
                >
                  <div className="flex items-start gap-3">
                    <span className="flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold flex-shrink-0" aria-hidden="true">
                      {a.name.charAt(0).toUpperCase()}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-display font-semibold text-[15px] text-campus-950 tracking-tight">{a.name}</p>
                      <p className="text-caption text-surface-400 mt-0.5 truncate">
                        {a.currentRole ? `${a.currentRole}${a.currentCompany ? ` @ ${a.currentCompany}` : ''}` : `${a.branch} '${a.graduationYear}`}
                      </p>
                    </div>
                  </div>
                  {skillList.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mt-3">
                      {skillList.slice(0, 4).map(s => (
                        <span key={s} className="rounded-full bg-campus-50 border border-campus-100 px-2.5 py-0.5 text-micro font-medium text-campus-700">{s}</span>
                      ))}
                      {skillList.length > 4 && <span className="text-micro text-surface-400 px-1 py-0.5">+{skillList.length - 4}</span>}
                    </div>
                  )}
                  {requestFor === a.id ? (
                    <div className="mt-3.5 space-y-2 animate-slide-down">
                      <div className="grid grid-cols-2 gap-2">
                        <Input value={company} onChange={e => setCompany(e.target.value)} placeholder="Company" aria-label={`Company for ${a.name}`} />
                        <Input value={role} onChange={e => setRole(e.target.value)} placeholder="Role" aria-label={`Role for ${a.name}`} />
                      </div>
                      <Input
                        value={requestMsg}
                        onChange={e => setRequestMsg(e.target.value)}
                        placeholder="Why are you a fit? Link your work…"
                        aria-label={`Message to ${a.name}`}
                      />
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="accent"
                          disabled={requestMutation.isPending}
                          loading={requestMutation.isPending}
                          onClick={() => requestMutation.mutate({ alumniId: a.id, message: requestMsg.trim(), company: company.trim(), role: role.trim() })}
                        >
                          <Send className="h-4 w-4" /> Send
                        </Button>
                        <button
                          onClick={() => { setRequestFor(null); setRequestMsg(''); setCompany(''); setRole('') }}
                          aria-label="Cancel request"
                          className="p-2 rounded-lg text-surface-400 hover:bg-surface-100 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      onClick={() => { setRequestFor(a.id); setRequestMsg(''); setCompany(a.currentCompany ?? ''); setRole('') }}
                      className="mt-3.5 w-full rounded-xl border border-campus-900/10 py-2 text-body-sm font-semibold text-campus-900 hover:bg-campus-500 hover:text-white hover:border-campus-500 transition-all focus:outline-none focus:ring-2 focus:ring-campus-500"
                    >
                      Request referral
                    </button>
                  )}
                </article>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
