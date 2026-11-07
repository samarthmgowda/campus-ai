import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader, LoadingState, ErrorState, EmptyState } from '../components/ui/PageState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { ArrowLeft, Calendar, CalendarX, MapPin, CheckCircle2, Loader2 } from 'lucide-react'

interface EventDetail {
  id: number
  title: string
  type: string
  eventDate: string | null
  description: string | null
  location: string | null
}

/**
 * Single-event view, reached from an event chat card or the events list.
 * Shows the details and lets a signed-in member RSVP.
 */
export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['event', id],
    queryFn: () => api.get<EventDetail>(`/events/${id}`),
    enabled: !!id,
  })

  const joinMutation = useMutation({
    mutationFn: () => api.post<{ attendeeCount: number; status: string }>(`/events/${id}/join`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event', id] })
      queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })

  if (!id) return <EmptyState icon={CalendarX} title="No event selected" hint="This link is missing an event id." />

  return (
    <PageContainer width="reading">
      <div className="mb-4">
        <Link
          to="/events"
          className="inline-flex items-center gap-1.5 text-body-sm font-medium text-campus-600 hover:text-campus-700"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> All events
        </Link>
      </div>

      {isLoading ? (
        <LoadingState label="Loading event…" />
      ) : error ? (
        <ErrorState title="Couldn't load this event" message={(error as Error).message} onRetry={() => refetch()} />
      ) : !data ? (
        <EmptyState icon={CalendarX} title="Event not found" hint="We couldn't find that event." />
      ) : (
        <>
          <PageHeader title={data.title} sub={data.type} />

          <div className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5 sm:p-6">
            <div className="flex flex-wrap gap-2 mb-4">
              {data.type && <Badge variant="info">{data.type}</Badge>}
            </div>
            <div className="space-y-2.5">
              <div className="flex items-center gap-2.5 text-body-sm text-surface-700">
                <Calendar className="h-4 w-4 text-campus-500 flex-shrink-0" aria-hidden="true" />
                {data.eventDate ?? 'Date to be announced'}
              </div>
              <div className="flex items-center gap-2.5 text-body-sm text-surface-700">
                <MapPin className="h-4 w-4 text-campus-500 flex-shrink-0" aria-hidden="true" />
                {data.location ?? 'Location to be announced'}
              </div>
            </div>
            {data.description && (
              <p className="mt-4 text-body-sm text-surface-600 leading-relaxed whitespace-pre-line">{data.description}</p>
            )}
          </div>

          <div className="mt-5 flex items-center gap-3">
            <Button
              variant="accent"
              onClick={() => joinMutation.mutate()}
              disabled={joinMutation.isPending || joinMutation.isSuccess}
            >
              {joinMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : joinMutation.isSuccess ? (
                <>
                  <CheckCircle2 className="h-4 w-4" /> You&apos;re going
                </>
              ) : (
                'RSVP'
              )}
            </Button>
            {joinMutation.isSuccess && (
              <span className="text-body-sm text-surface-500">
                Attendees: {joinMutation.data.attendeeCount}
              </span>
            )}
            {joinMutation.isError && (
              <span className="text-body-sm text-red-600">{(joinMutation.error as Error).message}</span>
            )}
          </div>
        </>
      )}
    </PageContainer>
  )
}
