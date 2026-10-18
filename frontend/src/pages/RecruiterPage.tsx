import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Briefcase, Plus, X, Users, Undo2, Check, Ban, BriefcaseBusiness } from 'lucide-react'
import { cn } from '../utils/cn'

interface PostingDto {
  id: number
  recruiterId: number | null
  recruiterName: string | null
  company: string | null
  title: string
  description: string | null
  requiredSkills: string | null
  status: string
  createdAt: string | null
  applicantCount: number
}

interface ApplicationDto {
  id: number
  postingId: number | null
  postingTitle: string | null
  studentId: number | null
  studentName: string | null
  status: string
  createdAt: string | null
}

const FILTERS = ['OPEN', 'CLOSED', ''] as const

export default function RecruiterPage() {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<string>('OPEN')
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [skills, setSkills] = useState('')
  const [appsFor, setAppsFor] = useState<number | null>(null)

  const { data: postings = [], isLoading, error, refetch } = useQuery({
    queryKey: ['postings', statusFilter],
    queryFn: () => api.get<PostingDto[]>(`/recruiter/postings${statusFilter ? `?status=${statusFilter}` : ''}`),
  })

  const { data: myApps = [] } = useQuery({
    queryKey: ['my-applications'],
    queryFn: () => api.get<ApplicationDto[]>('/recruiter/applications/my'),
  })
  const appliedIds = new Set(myApps.map(a => a.postingId))

  const { data: applicants = [], isLoading: appsLoading } = useQuery({
    queryKey: ['posting-apps', appsFor],
    queryFn: () => api.get<ApplicationDto[]>(`/recruiter/postings/${appsFor}/applications`),
    enabled: appsFor !== null,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['postings'] })
    queryClient.invalidateQueries({ queryKey: ['my-applications'] })
    if (appsFor !== null) queryClient.invalidateQueries({ queryKey: ['posting-apps', appsFor] })
  }

  const createMutation = useMutation({
    mutationFn: (body: Record<string, string>) => api.post<PostingDto>('/recruiter/postings', body),
    onSuccess: () => { setTitle(''); setDescription(''); setSkills(''); setShowCreate(false); invalidate() },
  })

  const applyMutation = useMutation({
    mutationFn: (id: number) => api.post<ApplicationDto>(`/recruiter/postings/${id}/apply`, {}),
    onSuccess: () => invalidate(),
  })

  const withdrawMutation = useMutation({
    mutationFn: (appId: number) => api.post(`/recruiter/applications/${appId}/withdraw`, {}),
    onSuccess: () => invalidate(),
  })

  const decideMutation = useMutation({
    mutationFn: ({ postingId, appId, action }: { postingId: number; appId: number; action: string }) =>
      api.post<ApplicationDto>(`/recruiter/postings/${postingId}/applications/${appId}/decide`, { action }),
    onSuccess: () => invalidate(),
  })

  const closeMutation = useMutation({
    mutationFn: (id: number) => api.post<PostingDto>(`/recruiter/postings/${id}/close`, {}),
    onSuccess: () => invalidate(),
  })

  const anyError = createMutation.error ?? applyMutation.error ?? withdrawMutation.error ?? decideMutation.error ?? closeMutation.error

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="Careers"
        sub={myApps.length > 0 ? `${myApps.length} application${myApps.length === 1 ? '' : 's'} out · ${postings.length} roles listed` : 'Open roles and your applications'}
        actions={
          <Button size="sm" variant="accent" onClick={() => setShowCreate(!showCreate)} aria-expanded={showCreate}>
            {showCreate ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
            New posting
          </Button>
        }
      />

      {showCreate && (
        <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-elevated p-5 sm:p-6 mb-5 animate-slide-down">
          <p className="font-display font-semibold text-[17px] text-campus-950">Post a role</p>
          <p className="text-body-sm text-surface-500 mt-0.5">Publishes under your demo user as the recruiter</p>
          <div className="grid gap-3 sm:grid-cols-2 mt-4">
            <Input label="Title" value={title} onChange={e => setTitle(e.target.value)} placeholder="Backend Intern" />
            <Input label="Required skills" value={skills} onChange={e => setSkills(e.target.value)} placeholder="Java, SQL" />
            <div className="sm:col-span-2">
              <label htmlFor="posting-desc" className="block text-body-sm font-medium text-surface-700 mb-1.5">About the role</label>
              <textarea
                id="posting-desc"
                value={description}
                onChange={e => setDescription(e.target.value)}
                rows={2}
                placeholder="What they'll do, duration, stipend…"
                className="w-full rounded-xl border border-surface-300 px-3 py-2 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500 resize-none"
              />
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <Button size="sm" variant="accent" disabled={!title.trim() || createMutation.isPending} loading={createMutation.isPending}
              onClick={() => createMutation.mutate({ title: title.trim(), description: description.trim(), requiredSkills: skills.trim() })}>
              Publish
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setShowCreate(false)}>Cancel</Button>
          </div>
        </div>
      )}

      <div className="flex items-center gap-1.5 mb-5" role="group" aria-label="Filter postings">
        {FILTERS.map(s => (
          <button
            key={s || 'all'}
            onClick={() => setStatusFilter(s)}
            aria-pressed={statusFilter === s}
            className={cn(
              'rounded-xl px-3.5 py-2 text-body-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-campus-500',
              statusFilter === s ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white shadow-card' : 'bg-white border border-campus-900/10 text-surface-500 hover:text-campus-900'
            )}
          >
            {s === '' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
        <span className="text-caption text-surface-400 ml-2 tabular-nums">{postings.length} roles</span>
      </div>

      {anyError && (
        <p className="mb-4" role="alert"><Badge variant="danger">{(anyError as Error).message}</Badge></p>
      )}

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2"><SkeletonCard /><SkeletonCard /><SkeletonCard /><SkeletonCard /></div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : postings.length === 0 ? (
        <EmptyState
          icon={BriefcaseBusiness}
          title={statusFilter === 'OPEN' ? 'No open roles' : 'No roles here'}
          hint={statusFilter === 'OPEN' ? 'All caught up — post a role above or check closed ones.' : 'Post the first role and applications land here.'}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 stagger">
          {postings.map((p, i) => {
            const applied = appliedIds.has(p.id)
            const myApp = myApps.find(a => a.postingId === p.id)
            const open = p.status === 'OPEN'
            const skillList = (p.requiredSkills ?? '').split(',').map(s => s.trim()).filter(Boolean)
            return (
              <article
                key={p.id}
                style={{ ['--stagger' as string]: Math.min(i, 8) }}
                className={cn(
                  'rounded-2xl border bg-white shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 p-5',
                  open ? 'border-campus-900/[0.07]' : 'border-campus-900/[0.05] opacity-90'
                )}
              >
                <div className="flex items-start gap-3">
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] flex-shrink-0" aria-hidden="true">
                    <Briefcase className="h-5 w-5 text-accent-300" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="font-display font-semibold text-[16px] text-campus-950 tracking-tight leading-tight">{p.title}</p>
                      <Badge variant={open ? 'success' : 'outline'} size="sm">{p.status}</Badge>
                    </div>
                    <p className="text-caption text-surface-400 mt-0.5 truncate">{p.company ?? p.recruiterName ?? ''}</p>
                  </div>
                </div>
                {p.description && <p className="text-body-sm text-surface-600 mt-3 leading-relaxed line-clamp-2">{p.description}</p>}
                {skillList.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mt-3">
                    {skillList.slice(0, 5).map(s => (
                      <span key={s} className="rounded-full bg-campus-50 border border-campus-100 px-2.5 py-0.5 text-micro font-medium text-campus-700">{s}</span>
                    ))}
                  </div>
                )}
                <div className="flex items-center gap-2 mt-3.5 text-caption text-surface-400 tabular-nums">
                  <Users className="h-3.5 w-3.5" aria-hidden="true" />
                  {p.applicantCount} applicant{p.applicantCount === 1 ? '' : 's'}
                  {myApp && <Badge variant="outline" size="sm" className="ml-1">{myApp.status}</Badge>}
                </div>
                <div className="flex flex-wrap items-center gap-2 mt-3.5 pt-3.5 border-t border-campus-900/[0.06]">
                  {open && !applied && (
                    <Button size="sm" variant="accent" disabled={applyMutation.isPending} onClick={() => applyMutation.mutate(p.id)}>
                      Apply now
                    </Button>
                  )}
                  {applied && myApp && (
                    <Button size="sm" variant="outline" disabled={withdrawMutation.isPending} onClick={() => withdrawMutation.mutate(myApp.id)}>
                      <Undo2 className="h-4 w-4" /> Withdraw
                    </Button>
                  )}
                  <button
                    onClick={() => setAppsFor(appsFor === p.id ? null : p.id)}
                    aria-expanded={appsFor === p.id}
                    className={cn(
                      'rounded-lg px-2.5 py-1.5 text-body-sm font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500',
                      appsFor === p.id ? 'bg-campus-50 text-campus-800' : 'text-surface-400 hover:text-campus-900 hover:bg-campus-50'
                    )}
                  >
                    Applicants{appsFor === p.id ? ' ▲' : ''}
                  </button>
                  {open && (
                    <button
                      onClick={() => closeMutation.mutate(p.id)}
                      className="rounded-lg px-2.5 py-1.5 text-body-sm font-medium text-surface-300 hover:text-red-600 hover:bg-red-50 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500 ml-auto"
                    >
                      Close
                    </button>
                  )}
                </div>
                {appsFor === p.id && (
                  <div className="mt-3 rounded-xl bg-campus-50/70 border border-campus-100/70 p-2 animate-slide-down">
                    {appsLoading ? (
                      <p className="text-caption text-surface-400 text-center py-3">Loading applicants…</p>
                    ) : applicants.length === 0 ? (
                      <p className="text-caption text-surface-400 text-center py-3">No applicants yet — share the posting.</p>
                    ) : (
                      applicants.map(a => (
                        <div key={a.id} className="flex items-center gap-2 px-2 py-2 border-b border-campus-900/[0.05] last:border-0">
                          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white text-micro font-bold flex-shrink-0" aria-hidden="true">
                            {(a.studentName ?? '?').charAt(0).toUpperCase()}
                          </span>
                          <span className="text-body-sm text-surface-800 flex-1 truncate">{a.studentName ?? `Student #${a.studentId}`}</span>
                          <Badge variant={a.status === 'SHORTLISTED' ? 'success' : a.status === 'REJECTED' ? 'danger' : 'default'} size="sm">{a.status}</Badge>
                          {a.status === 'APPLIED' && (
                            <>
                              <button
                                onClick={() => decideMutation.mutate({ postingId: p.id, appId: a.id, action: 'SHORTLIST' })}
                                aria-label={`Shortlist ${a.studentName}`}
                                className="p-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                              >
                                <Check className="h-4 w-4" />
                              </button>
                              <button
                                onClick={() => decideMutation.mutate({ postingId: p.id, appId: a.id, action: 'REJECT' })}
                                aria-label={`Reject ${a.studentName}`}
                                className="p-1.5 rounded-lg text-surface-300 hover:text-red-600 hover:bg-red-50 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                              >
                                <Ban className="h-4 w-4" />
                              </button>
                            </>
                          )}
                        </div>
                      ))
                    )}
                  </div>
                )}
              </article>
            )
          })}
        </div>
      )}
    </div>
  )
}
