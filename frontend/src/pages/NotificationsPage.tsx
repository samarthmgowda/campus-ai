import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { cn } from '../utils/cn'
import { Bell, CheckCheck, Heart, MessageCircle, Briefcase, GraduationCap, Users, BellRing } from 'lucide-react'

interface NotificationDto {
  id: number
  recipientId: number | null
  recipientName: string | null
  type: string
  title: string
  body: string | null
  refType: string | null
  refId: number | null
  readFlag: boolean
  createdAt: string | null
}

function iconFor(type: string) {
  const t = type.toUpperCase()
  if (t.includes('LIKE') || t.includes('ENDORSE')) return { Icon: Heart, tint: 'bg-accent-50 text-accent-600 border-accent-100' }
  if (t.includes('COMMENT') || t.includes('MESSAGE')) return { Icon: MessageCircle, tint: 'bg-sky-50 text-sky-600 border-sky-100' }
  if (t.includes('JOB') || t.includes('POSTING') || t.includes('APPL')) return { Icon: Briefcase, tint: 'bg-violet-50 text-violet-600 border-violet-100' }
  if (t.includes('MENTOR')) return { Icon: GraduationCap, tint: 'bg-emerald-50 text-emerald-600 border-emerald-100' }
  if (t.includes('FOLLOW')) return { Icon: Users, tint: 'bg-amber-50 text-amber-600 border-amber-100' }
  return { Icon: Bell, tint: 'bg-campus-50 text-campus-600 border-campus-100' }
}

function dayLabel(iso: string | null) {
  if (!iso) return 'Earlier'
  try {
    const d = new Date(iso)
    const today = new Date()
    const yest = new Date(today)
    yest.setDate(today.getDate() - 1)
    if (d.toDateString() === today.toDateString()) return 'Today'
    if (d.toDateString() === yest.toDateString()) return 'Yesterday'
    return d.toLocaleDateString([], { weekday: 'long', month: 'short', day: 'numeric' })
  } catch {
    return 'Earlier'
  }
}

function timeOf(iso: string | null) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

export default function NotificationsPage() {
  const queryClient = useQueryClient()

  const { data: notifications = [], isLoading, error, refetch } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<NotificationDto[]>('/notifications'),
  })

  const unread = notifications.filter(n => !n.readFlag).length

  const readMutation = useMutation({
    mutationFn: (id: number) => api.post(`/notifications/${id}/read`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['badge-notif'] })
    },
  })

  const readAllMutation = useMutation({
    mutationFn: () => api.post('/notifications/read-all', {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['badge-notif'] })
    },
  })

  const sorted = [...notifications].sort((a, b) => {
    if (a.readFlag !== b.readFlag) return a.readFlag ? 1 : -1
    return new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime()
  })

  const groups: { label: string; items: NotificationDto[] }[] = []
  for (const n of sorted) {
    const label = dayLabel(n.createdAt)
    const g = groups.find(g => g.label === label)
    if (g) g.items.push(n)
    else groups.push({ label, items: [n] })
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        title="Notifications"
        sub={unread > 0 ? `${unread} unread — newest first` : 'You are all caught up'}
        actions={
          unread > 0 ? (
            <Button size="sm" variant="outline" onClick={() => readAllMutation.mutate()} disabled={readAllMutation.isPending} loading={readAllMutation.isPending}>
              <CheckCheck className="h-4 w-4" /> Mark all read
            </Button>
          ) : undefined
        }
      />

      {unread > 0 && (
        <div className="flex items-center gap-3 rounded-2xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white px-5 py-4 mb-6 shadow-card animate-fade-in">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 flex-shrink-0" aria-hidden="true">
            <BellRing className="h-5 w-5 text-accent-300" />
          </span>
          <div className="min-w-0">
            <p className="font-display font-semibold text-body-sm">{unread} thing{unread === 1 ? '' : 's'} need{unread === 1 ? 's' : ''} your attention</p>
            <p className="text-caption text-white/60">Likes, replies, applications, mentorship updates</p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-3"><SkeletonCard /><SkeletonCard /><SkeletonCard /></div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : notifications.length === 0 ? (
        <EmptyState
          icon={Bell}
          title="Nothing yet"
          hint="When people like your posts, reply, or act on your applications, it lands here."
        />
      ) : (
        <div className="space-y-6 stagger">
          {groups.map((g, gi) => (
            <section key={g.label} style={{ ['--stagger' as string]: gi }}>
              <p className="text-micro font-semibold text-surface-400 tracking-wide px-1 mb-2">{g.label.toUpperCase()}</p>
              <div className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden divide-y divide-campus-900/[0.05]">
                {g.items.map(n => {
                  const { Icon, tint } = iconFor(n.type)
                  return (
                    <div
                      key={n.id}
                      className={cn(
                        'flex items-start gap-3.5 px-4 py-3.5 transition-colors',
                        !n.readFlag ? 'bg-campus-50/50' : 'hover:bg-surface-50/60'
                      )}
                    >
                      <span className={cn('flex h-10 w-10 items-center justify-center rounded-xl border flex-shrink-0', tint)} aria-hidden="true">
                        <Icon className="h-[18px] w-[18px]" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className={cn('text-body-sm leading-snug', n.readFlag ? 'text-surface-600' : 'font-semibold text-campus-950')}>
                          {n.title}
                        </p>
                        {n.body && <p className="text-body-sm text-surface-500 mt-0.5 line-clamp-2">{n.body}</p>}
                        <p className="text-micro text-surface-400 mt-1 tabular-nums">
                          {timeOf(n.createdAt)}
                          {n.refType && n.refId ? ` · ${n.refType} #${n.refId}` : ''}
                        </p>
                      </div>
                      {!n.readFlag ? (
                        <span className="h-2.5 w-2.5 rounded-full bg-accent-500 flex-shrink-0 mt-1.5" aria-label="Unread" role="img" />
                      ) : (
                        <span className="w-2.5 flex-shrink-0" aria-hidden="true" />
                      )}
                      {!n.readFlag && (
                        <button
                          onClick={() => readMutation.mutate(n.id)}
                          aria-label={`Mark "${n.title}" as read`}
                          className="p-1.5 -m-1 rounded-lg text-surface-300 hover:text-campus-600 hover:bg-campus-100 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500 flex-shrink-0"
                        >
                          <CheckCheck className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  )
                })}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
