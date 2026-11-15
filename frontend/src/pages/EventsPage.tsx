import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Event as CampusEvent } from '../types'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { CalendarDays, MapPin, Plus, X, Check, CalendarPlus } from 'lucide-react'
import { cn } from '../utils/cn'
import { DragEventBuilder } from '../components/DragEventBuilder'

function dateBlock(iso: string) {
  try {
    const d = new Date(iso)
    return {
      month: d.toLocaleDateString([], { month: 'short' }).toUpperCase(),
      day: String(d.getDate()).padStart(2, '0'),
      weekday: d.toLocaleDateString([], { weekday: 'short' }),
    }
  } catch {
    return { month: '—', day: '—', weekday: '' }
  }
}

function typeStyle(type: string) {
  const t = (type ?? '').toUpperCase()
  if (t.includes('HACK')) return 'bg-er-900 text-er-200 border-er-500/25'
  if (t.includes('WORKSHOP')) return 'bg-er-900 text-er-300 border-white/10'
  if (t.includes('SEMINAR')) return 'bg-teal-500/12 text-teal-300 border border-teal-500/25'
  return 'bg-sky-500/12 text-sky-300 border-sky-500/25'
}

export default function EventsPage() {
  const queryClient = useQueryClient()
  const [upcomingOnly, setUpcomingOnly] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  const [justSubmitted, setJustSubmitted] = useState(false)

  const { data: events = [], isLoading, error, refetch } = useQuery({
    queryKey: ['events', upcomingOnly],
    queryFn: () => api.get<CampusEvent[]>(`/events${upcomingOnly ? '?upcoming=true' : ''}`),
  })

  const { data: myRsvps = [] } = useQuery({
    queryKey: ['my-rsvps'],
    queryFn: () => api.get<{ eventId: number }[]>('/events/my'),
  })

  const myEventIds = new Set(myRsvps.map(r => r.eventId))
  const joinedCount = myEventIds.size

  const joinMutation = useMutation({
    mutationFn: (eventId: number) => api.post(`/events/${eventId}/join`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events'] })
      queryClient.invalidateQueries({ queryKey: ['my-rsvps'] })
    },
  })

  const leaveMutation = useMutation({
    mutationFn: (eventId: number) => api.post(`/events/${eventId}/leave`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events'] })
      queryClient.invalidateQueries({ queryKey: ['my-rsvps'] })
    },
  })

  return (
    <PageContainer width="standard">
      <PageHeader
        title="Events"
        sub={joinedCount > 0 ? `You're in for ${joinedCount} · ${events.length} listed` : 'Hackathons, workshops, and campus gatherings'}
        actions={
          <div className="flex items-center gap-2">
            <button
              onClick={() => setUpcomingOnly(!upcomingOnly)}
              aria-pressed={upcomingOnly}
              className={cn(
                'rounded-xl px-3 py-2 text-body-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-er-500',
                upcomingOnly ? 'bg-gradient-to-br from-er-500 to-er-600 text-white shadow-card' : 'bg-er-800 border border-white/10 text-ink-400 hover:text-ink-100'
              )}
            >
              Upcoming only
            </button>
            <Button size="sm" variant="accent" onClick={() => setShowCreate(!showCreate)} aria-expanded={showCreate}>
              {showCreate ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
              New event
            </Button>
          </div>
        }
      />

      {justSubmitted && (
        <div className="rounded-2xl border border-amber-500/25 bg-amber-500/12 px-4 py-3 mb-4 flex items-start gap-2.5" role="status">
          <p className="text-body-sm text-amber-200 leading-relaxed">
            <strong className="font-semibold">Submitted for approval.</strong>{' '}
            Your event is pending admin review and will appear here once approved.
          </p>
          <button
            onClick={() => setJustSubmitted(false)}
            aria-label="Dismiss notice"
            className="ml-auto p-1 rounded-lg text-amber-200 hover:text-amber-200 hover:bg-amber-500/12 transition-colors focus:outline-none focus:ring-2 focus:ring-amber-500"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {showCreate && (
        <DragEventBuilder />
      )}

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2"><SkeletonCard /><SkeletonCard /><SkeletonCard /><SkeletonCard /></div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : events.length === 0 ? (
        <EmptyState
          icon={CalendarPlus}
          title={upcomingOnly ? 'Nothing upcoming' : 'No events yet'}
          hint={upcomingOnly ? 'Turn off the upcoming filter or create the next gathering.' : 'Create the first event — a hackathon, workshop, or meetup.'}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 stagger">
          {events.map((e, i) => {
            const joined = myEventIds.has(e.id)
            const { month, day, weekday } = dateBlock(e.eventDate)
            return (
              <article
                key={e.id}
                style={{ ['--stagger' as string]: Math.min(i, 8) }}
                className={cn(
                  'rounded-2xl border bg-er-800 shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 overflow-hidden',
                  joined ? 'border-er-500/40 ring-1 ring-er-500/20' : 'border-white/10'
                )}
              >
                <div className="p-5 flex gap-4">
                  <div
                    className={cn(
                      'flex flex-col items-center justify-center rounded-2xl w-[68px] h-[76px] flex-shrink-0 border',
                      joined ? 'bg-gradient-to-br from-er-500 to-er-600 text-white border-transparent' : 'bg-er-900 border-white/10'
                    )}
                    aria-label={`Event date ${e.eventDate}`}
                  >
                    <span className={cn('text-micro font-bold tracking-widest', joined ? 'text-er-200' : 'text-er-300')}>{month}</span>
                    <span className={cn('font-display font-bold text-[26px] leading-none tabular-nums', joined ? 'text-white' : 'text-ink-50')}>{day}</span>
                    <span className={cn('text-micro', joined ? 'text-white/60' : 'text-ink-400')}>{weekday}</span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <p className="font-display font-semibold text-[16px] text-ink-50 tracking-tight leading-tight">{e.title}</p>
                    </div>
                    <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                      <span className={cn('rounded-full border px-2 py-0.5 text-micro font-semibold', typeStyle(e.type))}>{e.type}</span>
                      {joined && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/12 border border-emerald-500/25 px-2 py-0.5 text-micro font-semibold text-emerald-300">
                          <Check className="h-3 w-3" aria-hidden="true" /> Joined
                        </span>
                      )}
                    </div>
                    {e.description && <p className="text-body-sm text-ink-300 mt-2 line-clamp-2 leading-relaxed">{e.description}</p>}
                    {e.location && (
                      <p className="text-caption text-ink-400 mt-1.5 flex items-center gap-1">
                        <MapPin className="h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" /> {e.location}
                      </p>
                    )}
                    <div className="mt-3">
                      {joined ? (
                        <Button size="sm" variant="outline" disabled={leaveMutation.isPending} onClick={() => leaveMutation.mutate(e.id)}>
                          Leave
                        </Button>
                      ) : (
                        <Button size="sm" variant="accent" disabled={joinMutation.isPending} onClick={() => joinMutation.mutate(e.id)}>
                          <CalendarDays className="h-4 w-4" /> Join
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      )}
      {(joinMutation.isError || leaveMutation.isError) && (
        <p className="mt-4" role="alert"><Badge variant="danger">{((joinMutation.error ?? leaveMutation.error) as Error)?.message}</Badge></p>
      )}
    </PageContainer>
  )
}