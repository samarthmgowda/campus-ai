import { Link } from 'react-router-dom'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { useSession } from '../utils/identity'
import { PageHeader, SkeletonCard } from '../components/ui/PageState'
import { Badge } from '../components/ui/Badge'
import {
  MessageSquare, Users, CalendarDays, FolderKanban,
  Bell, Inbox, ArrowUpRight, Sparkles, GraduationCap,
} from 'lucide-react'

interface FeedPost { id: number; authorName: string | null; content: string; likeCount: number; commentCount: number; createdAt: string | null }
interface Project { id: number; name: string; domain: string; status: string; members: unknown[] }
interface CampusEvent { id: number; title: string; type: string; eventDate: string; location?: string }
interface Posting { id: number; title: string; company: string | null; status: string }
interface Conversation { peerId: number; unreadCount: number }
interface Notification { id: number; readFlag: boolean }

const QUICK = [
  { to: '/connect', icon: Users, label: 'Connect', hint: 'Network & Grow', tint: 'bg-gradient-to-br from-er-500 to-er-600 text-white' },
  { to: '/community', icon: Users, label: 'Community', hint: 'Join the wall', tint: 'bg-er-900 text-er-300 border border-white/10' },
  { to: '/projects', icon: FolderKanban, label: 'Projects', hint: 'Ship together', tint: 'bg-teal-500/12 text-teal-300 border border-teal-500/25' },
  { to: '/events', icon: CalendarDays, label: 'Events', hint: 'Show up', tint: 'bg-er-900 text-er-200 border border-er-500/25' },
  { to: '/placement-training', icon: GraduationCap, label: 'Placements', hint: 'Careers & Roadmaps', tint: 'bg-emerald-500/12 text-emerald-300 border border-emerald-500/25' },
  { to: '/team-requests', icon: Users, label: 'Team Requests', hint: 'Find teammates', tint: 'bg-sky-500/12 text-sky-300 border border-sky-500/25' },
]

function greeting() {
  const h = new Date().getHours()
  if (h < 5) return 'Up late'
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Good afternoon'
  return 'Good evening'
}

export default function HomePage() {
  // Reactive: a user switch re-renders this page with the new name and refetches
  // under new keys, rather than keeping the previous account's greeting.
  const session = useSession()
  const account = session?.userId ?? null
  const meName = session?.name ? `, ${session.name.split(' ')[0]}` : ''

  // Keyed under `home` so a single ['home'] invalidation (see App.tsx) refreshes
  // the whole dashboard whenever any mutation succeeds elsewhere in the app.
  // The account id is part of every key: a key that names its owner can never
  // show one account's inbox to another, even if the cache were not cleared.
  const inbox = useQuery({ queryKey: ['home', 'inbox', account], queryFn: () => api.get<Conversation[]>('/messages/inbox'), enabled: !!account })
  const notifs = useQuery({ queryKey: ['home', 'notifs', account], queryFn: () => api.get<Notification[]>('/notifications'), enabled: !!account })
  const events = useQuery({ queryKey: ['home', 'events', account], queryFn: () => api.get<CampusEvent[]>('/events?upcoming=true'), enabled: !!account })
  const projects = useQuery({ queryKey: ['home', 'projects', account], queryFn: () => api.get<Project[]>('/projects/mine'), enabled: !!account })
  const feed = useQuery({ queryKey: ['home', 'feed', account], queryFn: () => api.get<FeedPost[]>('/community/feed?limit=3'), enabled: !!account })
  const postings = useQuery({ queryKey: ['home', 'postings', account], queryFn: () => api.get<Posting[]>('/recruiter/postings?status=OPEN'), enabled: !!account })

  const unreadMsg = (inbox.data ?? []).reduce((s, c) => s + (c.unreadCount ?? 0), 0)
  const unreadNotif = (notifs.data ?? []).filter(n => !n.readFlag).length
  const loading = inbox.isLoading && notifs.isLoading && events.isLoading

  return (
    <PageContainer width="standard">
      <PageHeader
        title={`${greeting()}${meName}`}
        sub="Everything happening on your campus, in one glance"
        actions={
          (unreadMsg > 0 || unreadNotif > 0) ? (
            <div className="flex gap-1.5">
              {unreadNotif > 0 && <Badge variant="accent">{unreadNotif} unread</Badge>}
              {unreadMsg > 0 && <Badge variant="default">{unreadMsg} messages</Badge>}
            </div>
          ) : undefined
        }
      />

      <div className="relative overflow-hidden rounded-3xl text-white p-6 sm:p-8 mb-5 bg-gradient-to-br from-er-600 to-er-700 shadow-elevated">
        <div className="absolute -top-24 -right-16 h-72 w-72 rounded-full bg-white/10 blur-3xl" aria-hidden="true" />
        <div className="absolute -bottom-28 -left-12 h-60 w-60 rounded-full bg-white/[0.08] blur-3xl" aria-hidden="true" />
        <div className="relative flex flex-wrap items-center gap-5 justify-between">
          <div className="min-w-0">
            <p className="inline-flex items-center gap-1.5 rounded-full bg-white/10 border border-white/10 px-3 py-1 text-micro font-medium text-white">
              <Sparkles className="h-3.5 w-3.5 text-er-200" aria-hidden="true" />
              Campus at a glance
            </p>
            <div className="flex gap-6 sm:gap-8 mt-4">
              {[
                { v: projects.data?.length ?? '—', l: 'My projects' },
                { v: events.data?.length ?? '—', l: 'Upcoming events' },
                { v: postings.data?.length ?? '—', l: 'Open roles' },
              ].map(s => (
                <div key={s.l}>
                  <p className="font-display font-bold text-[30px] leading-none tabular-nums">{s.v}</p>
                  <p className="text-caption text-white/60 mt-1.5">{s.l}</p>
                </div>
              ))}
            </div>
          </div>
          <Link
            to="/chat"
            className="inline-flex items-center gap-2 rounded-2xl bg-er-500 hover:bg-er-600 active:scale-[0.98] transition-all px-5 py-3.5 font-semibold text-body-sm shadow-card focus:outline-none focus:ring-2 focus:ring-white/50"
          >
            <MessageSquare className="h-4 w-4" aria-hidden="true" />
            Ask Campus AI
            <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
      </div>

      {(unreadMsg > 0 || unreadNotif > 0) && (
        <div className="grid gap-2.5 sm:grid-cols-2 mb-5">
          {unreadNotif > 0 && (
            <Link to="/notifications" className="flex items-center gap-3 rounded-2xl border border-white/10 bg-er-800 shadow-card px-4 py-3.5 hover:shadow-card-hover hover:-translate-y-[1px] transition-all">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-er-900 border border-er-500/25 flex-shrink-0" aria-hidden="true">
                <Bell className="h-[18px] w-[18px] text-er-300" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-body-sm text-ink-50">{unreadNotif} unread notification{unreadNotif === 1 ? '' : 's'}</span>
                <span className="block text-caption text-ink-400">Catch up on what you missed</span>
              </span>
              <ArrowUpRight className="h-4 w-4 text-ink-500 flex-shrink-0" aria-hidden="true" />
            </Link>
          )}
          {unreadMsg > 0 && (
            <Link to="/messages" className="flex items-center gap-3 rounded-2xl border border-white/10 bg-er-800 shadow-card px-4 py-3.5 hover:shadow-card-hover hover:-translate-y-[1px] transition-all">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-er-900 border border-white/10 flex-shrink-0" aria-hidden="true">
                <Inbox className="h-[18px] w-[18px] text-er-300" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-body-sm text-ink-50">{unreadMsg} unread message{unreadMsg === 1 ? '' : 's'}</span>
                <span className="block text-caption text-ink-400">Your peers are waiting</span>
              </span>
              <ArrowUpRight className="h-4 w-4 text-ink-500 flex-shrink-0" aria-hidden="true" />
            </Link>
          )}
        </div>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5 mb-6" aria-label="Quick actions">
        {QUICK.map(q => (
          <Link
            key={q.to}
            to={q.to}
            className="group rounded-2xl border border-white/10 bg-er-800 shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 p-4 focus:outline-none focus:ring-2 focus:ring-er-500"
          >
            <span className={`flex h-10 w-10 items-center justify-center rounded-xl ${q.tint}`} aria-hidden="true">
              <q.icon className="h-5 w-5" />
            </span>
            <span className="block font-semibold text-body-sm text-ink-50 mt-2.5">{q.label}</span>
            <span className="block text-caption text-ink-400 mt-0.5">{q.hint}</span>
          </Link>
        ))}
      </div>

      {loading ? (
        <div className="grid gap-4 lg:grid-cols-2"><SkeletonCard /><SkeletonCard /></div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2 stagger">
          <section aria-label="Upcoming events" className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-5" style={{ ['--stagger' as string]: 0 }}>
            <div className="flex items-center justify-between mb-3.5">
              <p className="font-display font-semibold text-[16px] text-ink-50">Coming up</p>
              <Link to="/events" className="text-body-sm font-semibold text-er-300 hover:text-er-200 inline-flex items-center gap-0.5">
                All events <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
              </Link>
            </div>
            {(events.data ?? []).length === 0 ? (
              <p className="text-body-sm text-ink-400 py-4 text-center">Nothing scheduled — check back soon.</p>
            ) : (
              <div className="space-y-2">
                {(events.data ?? []).slice(0, 3).map(e => (
                  <div key={e.id} className="flex items-center gap-3 rounded-xl bg-white/[0.04] border border-white/10 px-3.5 py-2.5">
                    <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-er-500 to-er-600 text-white flex-shrink-0" aria-hidden="true">
                      <CalendarDays className="h-4 w-4" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-body-sm text-ink-50 truncate">{e.title}</p>
                      <p className="text-caption text-ink-400 truncate">{e.eventDate}{e.location ? ` · ${e.location}` : ''}</p>
                    </div>
                    <Badge variant="info" size="sm">{e.type}</Badge>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section aria-label="Latest on the wall" className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-5" style={{ ['--stagger' as string]: 1 }}>
            <div className="flex items-center justify-between mb-3.5">
              <p className="font-display font-semibold text-[16px] text-ink-50">On the wall</p>
              <Link to="/community" className="text-body-sm font-semibold text-er-300 hover:text-er-200 inline-flex items-center gap-0.5">
                Community <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
              </Link>
            </div>
            {(feed.data ?? []).length === 0 ? (
              <p className="text-body-sm text-ink-400 py-4 text-center">The wall is quiet — start a conversation.</p>
            ) : (
              <div className="space-y-2.5">
                {(feed.data ?? []).map(p => (
                  <div key={p.id}>
                    <p className="text-body-sm font-semibold text-ink-50 truncate">{p.authorName ?? 'Someone'}</p>
                    <p className="text-body-sm text-ink-300 line-clamp-2 mt-0.5 leading-relaxed">{p.content}</p>
                    <p className="text-micro text-ink-400 mt-1 tabular-nums">{p.likeCount} likes · {p.commentCount} replies</p>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </PageContainer>
  )
}
