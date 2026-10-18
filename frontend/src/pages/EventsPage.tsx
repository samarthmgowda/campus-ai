import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Event as CampusEvent } from '../types'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { CalendarDays, MapPin, Plus, X, Check, CalendarPlus } from 'lucide-react'
import { cn } from '../utils/cn'

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
  if (t.includes('HACK')) return 'bg-accent-50 text-accent-700 border-accent-100'
  if (t.includes('WORKSHOP')) return 'bg-campus-50 text-campus-700 border-campus-100'
  if (t.includes('SEMINAR')) return 'bg-violet-50 text-violet-700 border-violet-100'
  return 'bg-sky-50 text-sky-700 border-sky-100'
}

export default function EventsPage() {
  const queryClient = useQueryClient()
  const [upcomingOnly, setUpcomingOnly] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [type, setType] = useState('HACKATHON')
  const [date, setDate] = useState('')
  const [description, setDescription] = useState('')
  const [location, setLocation] = useState('')
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

  const createMutation = useMutation({
    mutationFn: (body: Record<string, string>) => api.post<CampusEvent>('/events', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events'] })
      setTitle(''); setType('HACKATHON'); setDate(''); setDescription(''); setLocation(''); setShowCreate(false)
      setJustSubmitted(true)
    },
  })

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
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="Events"
        sub={joinedCount > 0 ? `You're in for ${joinedCount} · ${events.length} listed` : 'Hackathons, workshops, and campus gatherings'}
        actions={
          <div className="flex items-center gap-2">
            <button
              onClick={() => setUpcomingOnly(!upcomingOnly)}
              aria-pressed={upcomingOnly}
              className={cn(
                'rounded-xl px-3 py-2 text-body-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-campus-500',
                upcomingOnly ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white shadow-card' : 'bg-white border border-campus-900/10 text-surface-500 hover:text-campus-900'
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
        <div className="rounded-2xl border border-amber-200/70 bg-amber-50/60 px-4 py-3 mb-4 flex items-start gap-2.5" role="status">
          <p className="text-body-sm text-amber-800 leading-relaxed">
            <strong className="font-semibold">Submitted for approval.</strong>{' '}
            Your event is pending admin review and will appear here once approved.
          </p>
          <button
            onClick={() => setJustSubmitted(false)}
            aria-label="Dismiss notice"
            className="ml-auto p-1 rounded-lg text-amber-600 hover:text-amber-900 hover:bg-amber-100 transition-colors focus:outline-none focus:ring-2 focus:ring-amber-500"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {showCreate && (
        <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-elevated p-5 sm:p-6 mb-6 animate-slide-down">
          <p className="font-display font-semibold text-[17px] text-campus-950">Create event</p>
          <p className="text-body-sm text-surface-500 mt-0.5">Type like HACKATHON, WORKSHOP, SEMINAR · new events need admin approval before publishing</p>
          <div className="grid gap-3 sm:grid-cols-2 mt-4">
            <Input label="Title" value={title} onChange={e => setTitle(e.target.value)} placeholder="Winter Hack Night" />
            <div className="w-full">
              <label htmlFor="evt-type" className="block text-body-sm font-medium text-surface-700 mb-1.5">Type</label>
              <select
                id="evt-type"
                value={type}
                onChange={e => setType(e.target.value)}
                className="w-full rounded-lg border border-surface-300 bg-white px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-campus-500 transition-colors"
              >
                <option value="HACKATHON">Hackathon</option>
                <option value="WORKSHOP">Workshop</option>
                <option value="SEMINAR">Seminar</option>
                <option value="NETWORKING">Networking</option>
                <option value="PLACEMENT">Placement Drive</option>
              </select>
            </div>
            <Input label="Date" type="date" value={date} onChange={e => setDate(e.target.value)} />
            <Input label="Location" value={location} onChange={e => setLocation(e.target.value)} placeholder="Innovation Hall A" />
            <div className="sm:col-span-2">
              <label htmlFor="evt-desc" className="block text-body-sm font-medium text-surface-700 mb-1.5">What is happening?</label>
              <textarea
                id="evt-desc"
                value={description}
                onChange={e => setDescription(e.target.value)}
                rows={2}
                placeholder="Agenda, guests, what to bring…"
                className="w-full rounded-xl border border-surface-300 px-3 py-2 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500 resize-none"
              />
            </div>
          </div>
          {createMutation.isError && (
            <p className="text-body-sm text-red-600 mt-3" role="alert">{(createMutation.error as Error).message}</p>
          )}
          <div className="flex gap-2 mt-4">
            <Button size="sm" variant="accent" disabled={!title.trim() || !date || createMutation.isPending} loading={createMutation.isPending}
              onClick={() => createMutation.mutate({ title: title.trim(), type: type.trim(), eventDate: date, description: description.trim(), location: location.trim() })}>
              Create
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setShowCreate(false)}>Cancel</Button>
          </div>
        </div>
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
                  'rounded-2xl border bg-white shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 overflow-hidden',
                  joined ? 'border-campus-500/30 ring-1 ring-campus-500/20' : 'border-campus-900/[0.07]'
                )}
              >
                <div className="p-5 flex gap-4">
                  <div
                    className={cn(
                      'flex flex-col items-center justify-center rounded-2xl w-[68px] h-[76px] flex-shrink-0 border',
                      joined ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white border-transparent' : 'bg-campus-50 border-campus-100'
                    )}
                    aria-label={`Event date ${e.eventDate}`}
                  >
                    <span className={cn('text-micro font-bold tracking-widest', joined ? 'text-accent-300' : 'text-accent-600')}>{month}</span>
                    <span className={cn('font-display font-bold text-[26px] leading-none tabular-nums', joined ? 'text-white' : 'text-campus-950')}>{day}</span>
                    <span className={cn('text-micro', joined ? 'text-white/60' : 'text-surface-400')}>{weekday}</span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <p className="font-display font-semibold text-[16px] text-campus-950 tracking-tight leading-tight">{e.title}</p>
                    </div>
                    <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                      <span className={cn('rounded-full border px-2 py-0.5 text-micro font-semibold', typeStyle(e.type))}>{e.type}</span>
                      {joined && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 border border-emerald-100 px-2 py-0.5 text-micro font-semibold text-emerald-700">
                          <Check className="h-3 w-3" aria-hidden="true" /> Joined
                        </span>
                      )}
                    </div>
                    {e.description && <p className="text-body-sm text-surface-600 mt-2 line-clamp-2 leading-relaxed">{e.description}</p>}
                    {e.location && (
                      <p className="text-caption text-surface-400 mt-1.5 flex items-center gap-1">
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
    </div>
  )
}
