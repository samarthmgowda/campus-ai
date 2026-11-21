import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { EmptyState, ErrorState } from '../components/ui/PageState'
import { Button } from '../components/ui/Button'
import { useState } from 'react'
import { Loader2, MapPin } from 'lucide-react'

interface EventDetail {
  id: number
  title: string
  type: string
  eventDate: string | null
  description: string | null
  location: string | null
  organizerId: number | null
}

interface Team {
  id: number
  name: string
  code: string
  ownerId: number
  members: number
  eventId: number
}

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const [showCreateTeam, setShowCreateTeam] = useState(false)
  const [showJoinTeam, setShowJoinTeam] = useState(false)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['event', id],
    queryFn: () => api.get<EventDetail>(`/events/${id}`),
    enabled: !!id,
  })

  // Team creation mutation
  const createTeamMutation = useMutation({
    mutationFn: (teamData: { name: string }) =>
      api.post<Team>(`/events/${id}/teams`, teamData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event', id] })
      setShowCreateTeam(false)
    },
  })

  // Join team by code mutation
  const joinTeamByCodeMutation = useMutation({
    mutationFn: (code: string) =>
      api.post<Team>(`/events/${id}/teams/join-by-code`, { code }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event', id] })
      setShowJoinTeam(false)
    },
  })

  // Get user's teams for this event
  const { data: myTeams = [] } = useQuery({
    queryKey: ['my-teams', id],
    queryFn: () => api.get<Team[]>(`/events/${id}/teams/my`),
    enabled: !!id,
  })

  if (!id) return <EmptyState icon={Loader2} title="No event selected" hint="This link is missing an event id." />

  if (isLoading) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <Loader2 className="h-8 w-8 mx-auto animate-spin text-er-400" aria-hidden="true" />
        <p className="mt-4 text-body-sm text-ink-400">Loading event…</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <div className="mb-4">
          <Link
            to="/events"
            className="inline-flex items-center gap-1.5 text-body-sm font-medium text-er-300 hover:text-er-300"
          >
            ← Back to events
          </Link>
        </div>
        <ErrorState title="Couldn't load this event" message={(error as Error).message} onRetry={() => refetch()} />
      </div>
    )
  }

  if (!data) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <div className="mb-4">
          <Link
            to="/events"
            className="inline-flex items-center gap-1.5 text-body-sm font-medium text-er-300 hover:text-er-300"
          >
            ← Back to events
          </Link>
        </div>
        <EmptyState icon={Loader2} title="Event not found" hint="We couldn't find that event." />
      </div>
    )
  }

  const event = data

  return (
    <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-5 sm:p-6">
      <div className="flex flex-wrap gap-2 mb-4">
        {event.type && <Button variant="accent" size="sm">{event.type}</Button>}
      </div>

      <div className="space-y-3">
        <div className="flex items-center gap-2.5 text-body-sm text-ink-200">
          <Loader2 className="h-4 w-4 text-er-400 flex-shrink-0" aria-hidden="true" />
          {event.eventDate ?? 'Date to be announced'}
        </div>
        <div className="flex items-center gap-2.5 text-body-sm text-ink-200">
          <MapPin className="h-4 w-4 text-er-400 flex-shrink-0" aria-hidden="true" />
          {event.location ?? 'Location to be announced'}
        </div>
        {event.description && (
          <p className="mt-3 text-body-sm text-ink-300 leading-relaxed whitespace-pre-line">{event.description}</p>
        )}
      </div>

      {/* Team Management Section */}
      <div className="mt-5 pt-4 border-t border-white/10">
        <h3 className="font-display font-semibold text-[13px] text-ink-50 mb-3">Teams</h3>

        {myTeams.length > 0 ? (
          <div className="space-y-2">
            {myTeams.map((t) => (
              <div
                key={t.id}
                className="rounded-xl bg-er-800/50 border border-white/10 p-3 mb-2"
              >
                <div className="flex items-center justify-between">
                  <span className="font-medium text-ink-50">{t.name}</span>
                  <span className="text-caption text-ink-400">{t.members}/5 members</span>
                </div>
                <div className="flex items-center gap-2 text-caption text-ink-400">
                  <span>Code: {t.code}</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-body-sm text-ink-400">
            No teams yet. Be the first to create one!
          </p>
        )}

        {/* Create team button */}
        <Button
          variant="accent"
          size="sm"
          onClick={() => setShowCreateTeam(true)}
        >
          Create Team
        </Button>

        {/* Join team by code button */}
        <Button
          variant="outline"
          size="sm"
          onClick={() => setShowJoinTeam(true)}
        >
          Join existing team (enter code)
        </Button>
      </div>

      {/* Create team modal */}
      {showCreateTeam && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg p-6 shadow-xl max-w-sm w-full">
            <h3 className="font-display font-semibold text-[16px] mb-4">Create New Team</h3>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                const name = (document.getElementById('team-name') as HTMLInputElement).value.trim()
                if (name && createTeamMutation.isPending !== true) {
                  createTeamMutation.mutate({ name })
                }
              }}
            >
              <div className="mb-4">
                <input
                  id="team-name"
                  type="text"
                  placeholder="My Team"
                  className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
                  required
                />
              </div>
              <div className="flex gap-2">
                <Button type="submit" variant="accent">
                  Create Team
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setShowCreateTeam(false)}
                >
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Join team by code modal */}
      {showJoinTeam && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg p-6 shadow-xl max-w-sm w-full">
            <h3 className="font-display font-semibold text-[16px] mb-4">
              Join Existing Team
            </h3>
            <p className="text-caption text-ink-400 mb-4">
              Enter the team code to join
            </p>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                const code = (document.getElementById('team-code') as HTMLInputElement).value.trim()
                if (code && joinTeamByCodeMutation.isPending !== true) {
                  joinTeamByCodeMutation.mutate(code)
                }
              }}
            >
              <div className="mb-4">
                <input
                  id="team-code"
                  type="text"
                  placeholder="TEAM123"
                  className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
                  required
                  maxLength={20}
                />
              </div>
              <div className="flex gap-2">
                <Button type="submit" variant="accent">
                  Join Team
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setShowJoinTeam(false)}
                >
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="mt-4 flex items-center gap-3">
        <Button variant="accent">Join Event</Button>
        <Button variant="outline">View Organizers</Button>
      </div>
    </div>
  )
}