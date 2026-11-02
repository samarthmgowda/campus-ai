import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Heart, MessageCircle, Bookmark, Flag, Send, MessagesSquare, Loader2, CheckCircle2 } from 'lucide-react'
import { cn } from '../utils/cn'

interface PostDto {
  id: number
  authorId: number | null
  authorName: string | null
  content: string
  createdAt: string | null
  likeCount: number
  commentCount: number
}

interface CommentDto {
  id: number
  postId: number
  authorId: number | null
  authorName: string | null
  content: string
  createdAt: string | null
}

interface BookmarkDto {
  id: number
  refType: string
  refId: number
  createdAt: string
}

const AVATARS = [
  'from-[#667eea] to-[#764ba2]',
  'from-accent-400 to-accent-600',
  'from-emerald-400 to-emerald-600',
  'from-violet-400 to-violet-600',
  'from-sky-400 to-sky-600',
  'from-amber-400 to-amber-600',
]

function avatarFor(name: string | null, id: number | null) {
  const key = (name ?? String(id ?? '?')).length + (id ?? 0)
  return AVATARS[key % AVATARS.length]
}

function timeAgo(iso: string | null) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  const days = Math.floor(hrs / 24)
  if (days < 7) return `${days}d ago`
  try {
    return new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

export default function CommunityPage() {
  const queryClient = useQueryClient()
  const [newPost, setNewPost] = useState('')
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [commentText, setCommentText] = useState('')
  const [reportId, setReportId] = useState<number | null>(null)
  const [reportReason, setReportReason] = useState('SPAM')
  const [reportDetails, setReportDetails] = useState('')
  const [reportDone, setReportDone] = useState<number | null>(null)

  const { data: posts = [], isLoading, error, refetch } = useQuery({
    queryKey: ['community-feed'],
    queryFn: () => api.get<PostDto[]>('/community/feed?limit=20'),
  })

  const { data: myBookmarks = [] } = useQuery({
    queryKey: ['my-bookmarks'],
    queryFn: () => api.get<BookmarkDto[]>('/bookmarks'),
  })
  const savedIds = new Set(myBookmarks.filter(b => b.refType === 'POST').map(b => b.refId))

  const { data: comments = [], isLoading: commentsLoading } = useQuery({
    queryKey: ['post-comments', expandedId],
    queryFn: () => api.get<CommentDto[]>(`/community/${expandedId}/comments`),
    enabled: expandedId !== null,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['community-feed'] })
    queryClient.invalidateQueries({ queryKey: ['my-bookmarks'] })
    if (expandedId !== null) queryClient.invalidateQueries({ queryKey: ['post-comments', expandedId] })
  }

  const createMutation = useMutation({
    mutationFn: (content: string) => api.post<PostDto>('/community', { content }),
    onSuccess: () => { setNewPost(''); invalidate() },
  })

  const likeMutation = useMutation({
    mutationFn: (id: number) => api.post<{ likeCount: number }>(`/community/${id}/like`, {}),
    onSuccess: () => invalidate(),
  })

  const commentMutation = useMutation({
    mutationFn: ({ postId, content }: { postId: number; content: string }) =>
      api.post<CommentDto>(`/community/${postId}/comment`, { content }),
    onSuccess: () => { setCommentText(''); invalidate() },
  })

  const bookmarkMutation = useMutation({
    mutationFn: (refId: number) => api.post('/bookmarks/toggle', { refType: 'POST', refId }),
    onSuccess: () => invalidate(),
  })

  const reportMutation = useMutation({
    mutationFn: ({ targetId, reason, details }: { targetId: number; reason: string; details: string }) =>
      api.post('/reports', { targetType: 'POST', targetId, reason, details }),
    onSuccess: (_d, vars) => { setReportId(null); setReportDetails(''); setReportDone(vars.targetId) },
  })

  return (
    <PageContainer width="form">
      <PageHeader
        title="Community"
        sub="The campus wall — questions, wins, and discussions"
        actions={<Badge variant="default">{posts.length} posts</Badge>}
      />

      {/* Composer */}
      <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-card p-4 mb-6 focus-within:ring-2 focus-within:ring-campus-500 focus-within:border-transparent transition-shadow">
        <label htmlFor="new-post" className="sr-only">Share something with campus</label>
        <textarea
          id="new-post"
          value={newPost}
          onChange={e => setNewPost(e.target.value)}
          rows={newPost ? 3 : 2}
          placeholder="What's happening on campus?"
          className="w-full resize-none bg-transparent text-body placeholder:text-surface-400 focus:outline-none"
        />
        {createMutation.isError && (
          <p className="text-body-sm text-red-600 mt-1" role="alert">{(createMutation.error as Error).message}</p>
        )}
        <div className="flex items-center justify-between mt-2 pt-2 border-t border-campus-900/[0.06]">
          <p className="text-caption text-surface-400">Be kind — posts are visible to all students</p>
          <Button
            size="sm"
            variant="accent"
            disabled={!newPost.trim() || createMutation.isPending}
            loading={createMutation.isPending}
            onClick={() => createMutation.mutate(newPost.trim())}
          >
            <Send className="h-4 w-4" /> Share
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="space-y-4">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : posts.length === 0 ? (
        <EmptyState
          icon={MessagesSquare}
          title="The wall is blank"
          hint="No posts yet — start the first conversation on campus."
        />
      ) : (
        <div className="space-y-4 stagger">
          {posts.map((p, i) => {
            const saved = savedIds.has(p.id)
            return (
            <article
              key={p.id}
              style={{ ['--stagger' as string]: Math.min(i, 8) }}
              className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card hover:shadow-card-hover transition-shadow duration-200 overflow-hidden"
            >
              <div className="p-5 pb-4">
                <div className="flex items-center gap-3">
                  <span className={cn('flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br text-white font-display font-bold text-body-sm flex-shrink-0', avatarFor(p.authorName, p.authorId))} aria-hidden="true">
                    {(p.authorName ?? '?').charAt(0).toUpperCase()}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-body-sm text-campus-950 truncate">{p.authorName ?? `User #${p.authorId}`}</p>
                    <p className="text-caption text-surface-400">{timeAgo(p.createdAt)}</p>
                  </div>
                  {reportDone === p.id ? (
                    <span className="inline-flex items-center gap-1 text-micro font-medium text-emerald-700">
                      <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" /> Reported
                    </span>
                  ) : (
                    <button
                      onClick={() => setReportId(reportId === p.id ? null : p.id)}
                      aria-label="Report post"
                      aria-expanded={reportId === p.id}
                      className="p-2 rounded-lg text-surface-300 hover:text-surface-500 hover:bg-surface-100 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                    >
                      <Flag className="h-4 w-4" />
                    </button>
                  )}
                </div>
                <p className="text-body leading-relaxed text-surface-800 mt-3 whitespace-pre-wrap">{p.content}</p>
              </div>

              <div className="flex items-center gap-1 px-3 pb-2">
                <button
                  onClick={() => likeMutation.mutate(p.id)}
                  aria-label={`Like post, ${p.likeCount} likes`}
                  className={cn(
                    'inline-flex items-center gap-1.5 rounded-xl px-3 py-2 text-body-sm font-semibold transition-all active:scale-95 focus:outline-none focus:ring-2 focus:ring-campus-500',
                    p.likeCount > 0 ? 'text-accent-600 hover:bg-accent-50' : 'text-surface-400 hover:bg-surface-100 hover:text-surface-600'
                  )}
                >
                  <Heart className={cn('h-[18px] w-[18px]', p.likeCount > 0 && 'fill-accent-500 text-accent-500')} aria-hidden="true" />
                  <span className="tabular-nums">{p.likeCount || ''}</span>
                </button>
                <button
                  onClick={() => setExpandedId(expandedId === p.id ? null : p.id)}
                  aria-expanded={expandedId === p.id}
                  className={cn(
                    'inline-flex items-center gap-1.5 rounded-xl px-3 py-2 text-body-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-campus-500',
                    expandedId === p.id ? 'text-campus-700 bg-campus-50' : 'text-surface-400 hover:bg-surface-100 hover:text-surface-600'
                  )}
                >
                  <MessageCircle className="h-[18px] w-[18px]" aria-hidden="true" />
                  <span className="tabular-nums">{p.commentCount || ''}</span>
                </button>
                <button
                  onClick={() => bookmarkMutation.mutate(p.id)}
                  aria-label={saved ? 'Remove bookmark' : 'Save post'}
                  aria-pressed={saved}
                  className={cn(
                    'inline-flex items-center gap-1.5 rounded-xl px-3 py-2 text-body-sm font-semibold transition-all active:scale-95 focus:outline-none focus:ring-2 focus:ring-campus-500',
                    saved ? 'text-campus-700 bg-campus-50' : 'text-surface-400 hover:bg-surface-100 hover:text-surface-600'
                  )}
                >
                  <Bookmark className={cn('h-[18px] w-[18px]', saved && 'fill-campus-500 text-campus-500')} aria-hidden="true" />
                </button>
              </div>

              {expandedId === p.id && (
                <div className="px-5 pb-4 animate-slide-down">
                  <div className="rounded-xl bg-campus-50/70 border border-campus-100/70 p-3.5 space-y-3">
                    {commentsLoading ? (
                      <div className="flex justify-center py-3"><Loader2 className="h-5 w-5 animate-spin text-campus-500" /></div>
                    ) : comments.length === 0 ? (
                      <p className="text-caption text-surface-400 text-center py-1">No replies yet — start the thread.</p>
                    ) : (
                      comments.map(c => (
                        <div key={c.id} className="flex gap-2.5">
                          <span className={cn('flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br text-white text-micro font-bold flex-shrink-0', avatarFor(c.authorName, c.authorId))} aria-hidden="true">
                            {(c.authorName ?? '?').charAt(0).toUpperCase()}
                          </span>
                          <div className="min-w-0 rounded-xl rounded-tl-sm bg-white border border-campus-900/[0.06] px-3 py-2 flex-1">
                            <p className="text-micro font-semibold text-campus-900">{c.authorName ?? 'User'}</p>
                            <p className="text-body-sm text-surface-700 mt-0.5">{c.content}</p>
                          </div>
                        </div>
                      ))
                    )}
                    <form
                      className="flex gap-2"
                      onSubmit={e => {
                        e.preventDefault()
                        if (commentText.trim()) commentMutation.mutate({ postId: p.id, content: commentText.trim() })
                      }}
                    >
                      <Input
                        value={commentText}
                        onChange={e => setCommentText(e.target.value)}
                        placeholder="Write a reply…"
                        aria-label="Write a reply"
                        className="!rounded-xl"
                      />
                      <Button type="submit" size="sm" disabled={!commentText.trim() || commentMutation.isPending} className="flex-shrink-0">
                        Reply
                      </Button>
                    </form>
                  </div>
                </div>
              )}

              {reportId === p.id && (
                <div className="mx-5 mb-4 rounded-xl border border-amber-200/70 bg-amber-50/60 p-3.5 animate-slide-down">
                  <div className="grid gap-2.5 sm:grid-cols-2">
                    <div>
                      <label htmlFor={`reason-${p.id}`} className="block text-body-sm font-medium text-surface-700 mb-1">Reason</label>
                      <select
                        id={`reason-${p.id}`}
                        value={reportReason}
                        onChange={e => setReportReason(e.target.value)}
                        className="w-full rounded-xl border border-surface-300 px-3 py-2 text-body-sm bg-white focus:outline-none focus:ring-2 focus:ring-campus-500"
                      >
                        <option value="SPAM">Spam</option>
                        <option value="HARASSMENT">Harassment</option>
                        <option value="INAPPROPRIATE">Inappropriate content</option>
                        <option value="OTHER">Other</option>
                      </select>
                    </div>
                    <Input label="Details" value={reportDetails} onChange={e => setReportDetails(e.target.value)} placeholder="What is wrong?" />
                  </div>
                  {reportMutation.isError && (
                    <p className="text-body-sm text-red-600 mt-2" role="alert">{(reportMutation.error as Error).message}</p>
                  )}
                  <div className="flex gap-2 mt-3">
                    <Button size="sm" variant="outline" disabled={reportMutation.isPending} loading={reportMutation.isPending}
                      onClick={() => reportMutation.mutate({ targetId: p.id, reason: reportReason, details: reportDetails.trim() })}>
                      Submit report
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setReportId(null)}>Cancel</Button>
                  </div>
                </div>
              )}
            </article>
            )
          })}
        </div>
      )}
    </PageContainer>
  )
}
