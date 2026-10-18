import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Document, Event as CampusEvent } from '../types'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { ShieldAlert, Eye, EyeOff, Trash2, FileText, ShieldCheck, Flag, CalendarPlus } from 'lucide-react'
import { cn } from '../utils/cn'

interface ReportDto {
  id: number
  reporterId: number | null
  reporterName: string | null
  targetType: string
  targetId: number | null
  reason: string
  details: string | null
  status: string
  resolution: string | null
  createdAt: string | null
  resolvedAt: string | null
}

const REPORT_FILTERS = ['OPEN', 'RESOLVED', 'DISMISSED', ''] as const

export default function AdminPage() {
  const queryClient = useQueryClient()
  const [reportStatus, setReportStatus] = useState<string>('OPEN')
  const [resolvingId, setResolvingId] = useState<number | null>(null)
  const [resolution, setResolution] = useState('')

  const { data: stats } = useQuery({
    queryKey: ['report-stats'],
    queryFn: () => api.get<Record<string, number>>('/reports/stats'),
  })

  const { data: reports = [], isLoading, error, refetch } = useQuery({
    queryKey: ['reports', reportStatus],
    queryFn: () => api.get<ReportDto[]>(`/reports?status=${reportStatus}`),
  })

  const { data: docs = [], isLoading: docsLoading } = useQuery({
    queryKey: ['admin-docs'],
    queryFn: () => api.get<Document[]>('/documents/admin/all'),
  })

  const { data: adminEvents = [], isLoading: adminEventsLoading } = useQuery({
    queryKey: ['admin-events'],
    queryFn: () => api.get<CampusEvent[]>('/events/admin/all'),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['reports'] })
    queryClient.invalidateQueries({ queryKey: ['report-stats'] })
    queryClient.invalidateQueries({ queryKey: ['admin-docs'] })
    queryClient.invalidateQueries({ queryKey: ['admin-events'] })
  }

  const resolveMutation = useMutation({
    mutationFn: ({ id, action, resolutionText }: { id: number; action: string; resolutionText: string }) =>
      api.post<ReportDto>(`/reports/${id}/resolve`, { action, resolution: resolutionText }),
    onSuccess: () => { setResolvingId(null); setResolution(''); invalidate() },
  })

  const visibilityMutation = useMutation({
    mutationFn: ({ id, hidden }: { id: number; hidden: boolean }) =>
      api.post<Document>(`/documents/admin/${id}/visibility`, { hidden }),
    onSuccess: () => invalidate(),
  })

  const deleteDocMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/documents/admin/${id}`),
    onSuccess: () => invalidate(),
  })

  const eventVisibilityMutation = useMutation({
    mutationFn: ({ id, hidden }: { id: number; hidden: boolean }) =>
      api.post<CampusEvent>(`/events/admin/${id}/visibility`, { hidden }),
    onSuccess: () => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })

  const anyError = resolveMutation.error ?? visibilityMutation.error ?? deleteDocMutation.error ?? eventVisibilityMutation.error
  const openCount = stats ? (stats.OPEN ?? stats.open ?? 0) : reports.filter(r => r.status === 'OPEN').length

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="Admin"
        sub={openCount > 0 ? `${openCount} reports need review — requires admin user` : 'Keep the community healthy'}
        actions={openCount > 0 ? <Badge variant="warning">{openCount} open</Badge> : <Badge variant="success">Queue clear</Badge>}
      />

      {stats && (
        <div className="grid gap-3 grid-cols-2 sm:grid-cols-4 mb-5" role="region" aria-label="Report statistics">
          {Object.entries(stats).map(([k, v]) => (
            <div
              key={k}
              className={cn(
                'rounded-2xl border px-4 py-3.5 shadow-card',
                k.toUpperCase() === 'OPEN' && Number(v) > 0
                  ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white border-transparent'
                  : 'bg-white border-campus-900/[0.07]'
              )}
            >
              <p className={cn('font-display font-bold text-[26px] leading-none tabular-nums', k.toUpperCase() === 'OPEN' && Number(v) > 0 ? 'text-white' : 'text-campus-950')}>{v}</p>
              <p className={cn('text-caption mt-1.5 capitalize', k.toUpperCase() === 'OPEN' && Number(v) > 0 ? 'text-white/60' : 'text-surface-500')}>{k.toLowerCase()}</p>
            </div>
          ))}
        </div>
      )}

      {anyError && (
        <p className="mb-4" role="alert"><Badge variant="danger">{(anyError as Error).message}</Badge></p>
      )}

      <section aria-label="Moderation queue" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden mb-5">
        <div className="px-5 pt-5 pb-4 border-b border-campus-900/[0.06] bg-campus-50/50">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div>
              <p className="font-display font-semibold text-[16px] text-campus-950 flex items-center gap-2">
                <Flag className="h-4 w-4 text-accent-500" aria-hidden="true" /> Reports
              </p>
              <p className="text-caption text-surface-400 mt-0.5">Review flagged content — resolve with an action and note</p>
            </div>
            <div className="flex gap-1.5 flex-wrap" role="group" aria-label="Filter reports">
              {REPORT_FILTERS.map(s => (
                <button
                  key={s || 'all'}
                  onClick={() => setReportStatus(s)}
                  aria-pressed={reportStatus === s}
                  className={cn(
                    'rounded-lg px-2.5 py-1.5 text-body-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-campus-500',
                    reportStatus === s ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white' : 'text-surface-400 hover:text-campus-900 hover:bg-campus-50'
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
          ) : reports.length === 0 ? (
            <EmptyState
              icon={ShieldCheck}
              title={reportStatus === 'OPEN' ? 'Queue is clear' : 'No reports here'}
              hint="Flagged posts land here for review — nothing to action right now."
            />
          ) : (
            <div className="space-y-2.5 stagger">
              {reports.map((r, i) => (
                <div
                  key={r.id}
                  style={{ ['--stagger' as string]: Math.min(i, 8) }}
                  className={cn(
                    'rounded-xl border p-4',
                    r.status === 'OPEN' ? 'border-amber-200/70 bg-amber-50/40' : 'border-campus-900/[0.06] bg-white'
                  )}
                >
                  <div className="flex items-center gap-2 flex-wrap">
                    <ShieldAlert className="h-4 w-4 text-accent-500 flex-shrink-0" aria-hidden="true" />
                    <Badge variant="outline" size="sm">{r.targetType} #{r.targetId}</Badge>
                    <Badge variant="default" size="sm">{r.reason}</Badge>
                    <Badge variant={r.status === 'OPEN' ? 'warning' : 'success'} size="sm">{r.status}</Badge>
                    <span className="text-caption text-surface-400 ml-auto">by {r.reporterName ?? `#${r.reporterId}`}</span>
                  </div>
                  {r.details && <p className="text-body-sm text-surface-600 mt-2 leading-relaxed">{r.details}</p>}
                  {r.resolution && <p className="text-body-sm text-surface-500 mt-1.5 italic">Resolution: {r.resolution}</p>}
                  {r.status === 'OPEN' && (
                    resolvingId === r.id ? (
                      <div className="mt-3 flex gap-2 flex-wrap animate-slide-down">
                        <div className="flex-1 min-w-[180px]">
                          <Input
                            value={resolution}
                            onChange={e => setResolution(e.target.value)}
                            placeholder="Resolution note…"
                            aria-label={`Resolution note for report ${r.id}`}
                          />
                        </div>
                        <Button size="sm" variant="accent" disabled={resolveMutation.isPending}
                          onClick={() => resolveMutation.mutate({ id: r.id, action: 'RESOLVED', resolutionText: resolution.trim() })}>
                          Resolve
                        </Button>
                        <Button size="sm" variant="outline" disabled={resolveMutation.isPending}
                          onClick={() => resolveMutation.mutate({ id: r.id, action: 'DISMISSED', resolutionText: resolution.trim() })}>
                          Dismiss
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => { setResolvingId(null); setResolution('') }}>Cancel</Button>
                      </div>
                    ) : (
                      <Button size="sm" variant="outline" className="mt-3" onClick={() => { setResolvingId(r.id); setResolution('') }}>
                        Review
                      </Button>
                    )
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <section aria-label="Event approvals" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden mb-5">
        <div className="px-5 pt-5 pb-4 border-b border-campus-900/[0.06]">
          <p className="font-display font-semibold text-[16px] text-campus-950 flex items-center gap-2">
            <CalendarPlus className="h-4 w-4 text-accent-500" aria-hidden="true" /> Event approvals
          </p>
          <p className="text-caption text-surface-400 mt-0.5">
            New events start pending — approve to publish, hide to unpublish ·{' '}
            {adminEvents.filter(e => e.hidden).length} pending
          </p>
        </div>
        <div className="p-4 sm:p-5">
          {adminEventsLoading ? (
            <div className="space-y-2"><SkeletonCard /><SkeletonCard /></div>
          ) : adminEvents.length === 0 ? (
            <EmptyState icon={CalendarPlus} title="No events" hint="Created events appear here for approval." />
          ) : (
            <div className="space-y-1.5">
              {adminEvents.map(e => (
                <div key={e.id} className="flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-campus-50/70 transition-colors">
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-surface-100 flex-shrink-0" aria-hidden="true">
                    <CalendarPlus className="h-4 w-4 text-surface-500" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-surface-900 text-body-sm truncate">{e.title}</p>
                    <p className="text-micro text-surface-400 truncate">{e.type} · {e.eventDate}{e.location ? ` · ${e.location}` : ''}</p>
                    {e.hidden
                      ? <Badge variant="warning" size="sm" className="mt-0.5">Pending approval</Badge>
                      : <Badge variant="success" size="sm" className="mt-0.5">Published</Badge>}
                  </div>
                  {e.hidden ? (
                    <Button size="sm" variant="accent" disabled={eventVisibilityMutation.isPending}
                      onClick={() => eventVisibilityMutation.mutate({ id: e.id, hidden: false })}>
                      Approve
                    </Button>
                  ) : (
                    <button
                      onClick={() => eventVisibilityMutation.mutate({ id: e.id, hidden: true })}
                      aria-label={`Hide ${e.title}`}
                      title="Hide"
                      className="p-2 rounded-lg text-surface-400 hover:text-campus-700 hover:bg-campus-100 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                    >
                      <EyeOff className="h-4 w-4" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <section aria-label="Document curation" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden">
        <div className="px-5 pt-5 pb-4 border-b border-campus-900/[0.06]">
          <p className="font-display font-semibold text-[16px] text-campus-950">Documents</p>
          <p className="text-caption text-surface-400 mt-0.5">All uploads including hidden — toggle visibility or delete · {docs.length} total</p>
        </div>
        <div className="p-4 sm:p-5">
          {docsLoading ? (
            <div className="space-y-2"><SkeletonCard /><SkeletonCard /></div>
          ) : docs.length === 0 ? (
            <EmptyState icon={FileText} title="No documents" hint="Uploaded and created documents appear here for curation." />
          ) : (
            <div className="space-y-1.5">
              {docs.map(d => (
                <div key={d.id} className="flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-campus-50/70 transition-colors">
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-surface-100 flex-shrink-0" aria-hidden="true">
                    <FileText className="h-4 w-4 text-surface-500" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-surface-900 text-body-sm truncate">{d.title}</p>
                    {d.hidden && <Badge variant="warning" size="sm" className="mt-0.5">Hidden from AI</Badge>}
                  </div>
                  <button
                    onClick={() => visibilityMutation.mutate({ id: d.id, hidden: !d.hidden })}
                    aria-label={d.hidden ? `Unhide ${d.title}` : `Hide ${d.title}`}
                    title={d.hidden ? 'Unhide' : 'Hide'}
                    className="p-2 rounded-lg text-surface-400 hover:text-campus-700 hover:bg-campus-100 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                  >
                    {d.hidden ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
                  </button>
                  <button
                    onClick={() => { if (confirm(`Delete "${d.title}" and its chunks?`)) deleteDocMutation.mutate(d.id) }}
                    aria-label={`Delete ${d.title}`}
                    title="Delete"
                    className="p-2 rounded-lg text-surface-300 hover:text-red-600 hover:bg-red-50 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
