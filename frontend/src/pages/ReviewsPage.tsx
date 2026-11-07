import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { getId } from '../utils/identity'
import type { Student } from '../types'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Star, Award, Send } from 'lucide-react'
import { cn } from '../utils/cn'

interface ReviewDto {
  id: number
  revieweeId: number
  revieweeName: string | null
  reviewerId: number
  reviewerName: string | null
  rating: number
  comment: string | null
  projectId: number | null
  projectName: string | null
  createdAt: string | null
}

interface ReviewBundle {
  reviews: ReviewDto[]
  reputation: { studentId: number; reviewCount: number; averageRating: number | null }
}

function myId(): number | null {
  const v = getId()
  return v ? Number(v) : null
}

function Stars({ value, onPick, size = 'md' }: { value: number; onPick?: (n: number) => void; size?: 'sm' | 'md' }) {
  const cls = size === 'sm' ? 'h-3.5 w-3.5' : 'h-6 w-6'
  return (
    <div className="flex items-center gap-0.5" role={onPick ? 'radiogroup' : undefined} aria-label={onPick ? 'Pick a rating' : `${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map(n => (
        onPick ? (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n} star${n === 1 ? '' : 's'}`}
            onClick={() => onPick(n)}
            className="p-0.5 rounded focus:outline-none focus:ring-2 focus:ring-campus-500 transition-transform active:scale-90"
          >
            <Star className={cn(cls, n <= value ? 'fill-amber-400 text-amber-400' : 'text-surface-300')} aria-hidden="true" />
          </button>
        ) : (
          <Star key={n} className={cn(cls, n <= Math.round(value) ? 'fill-amber-400 text-amber-400' : 'text-surface-300')} aria-hidden="true" />
        )
      ))}
    </div>
  )
}

export default function ReviewsPage() {
  const queryClient = useQueryClient()
  const me = myId()
  const [peerId, setPeerId] = useState('')
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')
  const [showForm, setShowForm] = useState(false)

  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => api.get<Student[]>('/users'),
  })

  const { data: bundle, isLoading, error, refetch } = useQuery({
    queryKey: ['reviews-student', peerId],
    queryFn: () => api.get<ReviewBundle>(`/reviews/student/${peerId}`),
    enabled: peerId !== '',
  })

  const writeMutation = useMutation({
    mutationFn: (body: { revieweeId: number; rating: number; comment: string }) =>
      api.post<ReviewDto>('/reviews', body),
    onSuccess: () => {
      setComment(''); setRating(5); setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['reviews-student', peerId] })
    },
  })

  const peers = users.filter(u => u.id !== me)
  const rep = bundle?.reputation

  return (
    <PageContainer width="reading">
      <PageHeader
        title="Reviews"
        sub="Peer reputation after projects and events — evidence, not opaque scores"
        actions={
          peerId ? (
            <Button size="sm" variant="accent" onClick={() => setShowForm(!showForm)} aria-expanded={showForm}>
              <Send className="h-4 w-4" /> Write review
            </Button>
          ) : undefined
        }
      />

      <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-card p-4 mb-5">
        <label htmlFor="review-peer" className="block text-body-sm font-medium text-surface-700 mb-1.5">Whose reputation?</label>
        <select
          id="review-peer"
          value={peerId}
          onChange={e => { setPeerId(e.target.value); setShowForm(false) }}
          className="w-full rounded-xl border border-surface-300 px-3 py-2.5 text-body-sm bg-white focus:outline-none focus:ring-2 focus:ring-campus-500"
        >
          <option value="">Choose a student…</option>
          {peers.map(u => (
            <option key={u.id} value={u.id}>{u.name} · {u.branch}</option>
          ))}
        </select>
      </div>

      {!peerId ? (
        <EmptyState
          icon={Award}
          title="Pick a student"
          hint="See their rating, read what teammates said, and leave your own review after collaborating."
        />
      ) : isLoading ? (
        <div className="space-y-3"><SkeletonCard /><SkeletonCard /></div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : (
        <>
          <div className="flex items-center gap-4 rounded-2xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white px-5 py-4 mb-4 shadow-card">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/10 flex-shrink-0" aria-hidden="true">
              <Award className="h-6 w-6 text-accent-300" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-display font-bold text-[22px] leading-none tabular-nums">
                {rep?.averageRating != null ? rep.averageRating.toFixed(1) : '—'}
                <span className="text-body-sm font-medium text-white/50"> / 5</span>
              </p>
              <p className="text-caption text-white/60 mt-1">
                {rep?.reviewCount ?? 0} review{(rep?.reviewCount ?? 0) === 1 ? '' : 's'}
              </p>
            </div>
            {rep?.averageRating != null && <Stars value={rep.averageRating} />}
          </div>

          {showForm && (
            <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-elevated p-5 mb-4 animate-slide-down">
              <p className="font-display font-semibold text-[16px] text-campus-950">
                Review {bundle?.reviews[0]?.revieweeName ?? 'student'}
              </p>
              <p className="text-caption text-surface-400 mt-0.5 mb-3">Posted as you — identity comes from your sign-in</p>
              <Stars value={rating} onPick={setRating} />
              <div className="mt-3">
                <Input
                  label="What stood out?"
                  value={comment}
                  onChange={e => setComment(e.target.value)}
                  placeholder="Reliable on deadlines, strong on React…"
                />
              </div>
              {writeMutation.isError && (
                <p className="text-body-sm text-red-600 mt-2" role="alert">{(writeMutation.error as Error).message}</p>
              )}
              <div className="flex gap-2 mt-3">
                <Button
                  size="sm"
                  variant="accent"
                  disabled={writeMutation.isPending}
                  loading={writeMutation.isPending}
                  onClick={() => writeMutation.mutate({ revieweeId: Number(peerId), rating, comment: comment.trim() })}
                >
                  Post review
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setShowForm(false)}>Cancel</Button>
              </div>
            </div>
          )}

          {(bundle?.reviews ?? []).length === 0 ? (
            <EmptyState
              icon={Star}
              title="No reviews yet"
              hint="Nobody has reviewed this student — yours could be the first."
              action={<Button size="sm" variant="accent" onClick={() => setShowForm(true)}>Write the first</Button>}
            />
          ) : (
            <div className="space-y-2.5 stagger">
              {(bundle?.reviews ?? []).map((r, i) => (
                <article
                  key={r.id}
                  style={{ ['--stagger' as string]: Math.min(i, 8) }}
                  className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card px-4 py-3.5"
                >
                  <div className="flex items-center gap-2.5">
                    <span className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white text-body-sm font-bold flex-shrink-0" aria-hidden="true">
                      {(r.reviewerName ?? '?').charAt(0).toUpperCase()}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-body-sm text-campus-950 truncate">{r.reviewerName ?? 'Someone'}</p>
                      {r.projectName && <p className="text-micro text-surface-400 truncate">on {r.projectName}</p>}
                    </div>
                    <Stars value={r.rating} size="sm" />
                    <Badge variant={r.rating >= 4 ? 'success' : r.rating === 3 ? 'default' : 'warning'} size="sm">
                      <span className="tabular-nums">{r.rating}/5</span>
                    </Badge>
                  </div>
                  {r.comment && <p className="text-body-sm text-surface-600 mt-2 leading-relaxed">“{r.comment}”</p>}
                </article>
              ))}
            </div>
          )}
        </>
      )}
    </PageContainer>
  )
}
