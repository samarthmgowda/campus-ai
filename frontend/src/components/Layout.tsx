import { useState, useEffect } from 'react'
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { cn } from '../utils/cn'
import { getName, getRole, getId, clearIdentity, ROLE_LABELS, type Role } from '../utils/identity'
import {
  MessageSquare,
  FileText,
  FolderKanban,
  Calendar,
  User,
  Settings,
  LogOut,
  Menu,
  X,
  ChevronDown,
  Bot,
  Sparkles,
  Users,
  Inbox,
  Bell,
  GraduationCap,
  Briefcase,
  ShieldAlert,
  BarChart3,
  FlaskConical,
  LayoutDashboard,
  Search,
  Award,
  Map,
  Network,
} from 'lucide-react'

const NAV_GROUPS = [
  {
    label: 'Start',
    items: [
      { path: '/home', label: 'Home', icon: LayoutDashboard, badge: null },
      { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, badge: null },
      { path: '/chat', label: 'Ask AI', icon: MessageSquare, badge: null },
      { path: '/search', label: 'Search', icon: Search, badge: null },
    ],
  },
  {
    label: 'Campus life',
    items: [
      { path: '/community', label: 'Community', icon: Users, badge: null },
      { path: '/messages', label: 'Messages', icon: Inbox, badge: 'inbox' },
      { path: '/notifications', label: 'Notifications', icon: Bell, badge: 'notif' },
      { path: '/projects', label: 'Projects', icon: FolderKanban, badge: null },
      { path: '/events', label: 'Events', icon: Calendar, badge: null },
    ],
  },
  {
    label: 'Grow',
    items: [
      { path: '/mentorship', label: 'Mentorship', icon: GraduationCap, badge: null },
      { path: '/reviews', label: 'Reviews', icon: Award, badge: null },
      { path: '/skills-graph', label: 'Skill Graph', icon: Network, badge: null },
      { path: '/roadmaps', label: 'Roadmaps', icon: Map, badge: null },
      { path: '/referrals', label: 'Referrals', icon: Briefcase, badge: null },
      { path: '/dashboard/alumni', label: 'Alumni Hub', icon: GraduationCap, badge: null },
      { path: '/recruiter', label: 'Careers', icon: Briefcase, badge: null },
      { path: '/dashboard/recruiter', label: 'Recruiter Hub', icon: Briefcase, badge: null },
      { path: '/documents', label: 'Library', icon: FileText, badge: null },
    ],
  },
  {
    label: 'Manage',
    items: [
      { path: '/analytics', label: 'Analytics', icon: BarChart3, badge: null },
      { path: '/eval', label: 'Evaluation', icon: FlaskConical, badge: null },
      { path: '/admin', label: 'Admin', icon: ShieldAlert, badge: null },
      { path: '/profile', label: 'Profile', icon: User, badge: null },
      { path: '/settings', label: 'Settings', icon: Settings, badge: null },
    ],
  },
] as const

const TITLES: Record<string, { title: string; sub: string }> = {
  '/home': { title: 'Good day on campus', sub: 'Everything happening, in one glance' },
  '/chat': { title: 'Ask Campus AI', sub: 'Grounded answers from your college knowledge base' },
  '/search': { title: 'Search', sub: 'People, projects, events, documents' },
  '/reviews': { title: 'Reviews', sub: 'Peer reputation and trust' },
  '/roadmaps': { title: 'Career roadmaps', sub: 'Alumni guides to your target role' },
  '/referrals': { title: 'Referrals', sub: 'Job referrals from alumni who opt in' },
  '/community': { title: 'Community', sub: 'Conversations across campus' },
  '/messages': { title: 'Messages', sub: 'Direct lines to your peers' },
  '/notifications': { title: 'Notifications', sub: 'Never miss what matters' },
  '/documents': { title: 'Library', sub: 'Curated knowledge, ready to ask about' },
  '/projects': { title: 'Projects', sub: 'Build together, ship together' },
  '/events': { title: 'Events', sub: 'Hackathons, workshops, gatherings' },
  '/mentorship': { title: 'Mentorship', sub: 'Learn from those who walked it' },
  '/recruiter': { title: 'Careers', sub: 'Roles and your applications' },
  '/dashboard/alumni': { title: 'Alumni Dashboard', sub: 'Mentorship and referrals' },
  '/dashboard/recruiter': { title: 'Recruiter Dashboard', sub: 'Postings and applications' },
  '/analytics': { title: 'Analytics', sub: 'The pulse of your campus' },
  '/eval': { title: 'Evaluation', sub: 'RAG and teammate research harness' },
  '/admin': { title: 'Admin', sub: 'Keep the community healthy' },
  '/profile': { title: 'Profile', sub: 'Your campus identity' },
  '/settings': { title: 'Settings', sub: 'Make it yours' },
}

function useBadge(kind: string | null): number | null {
  const inbox = useQuery({
    queryKey: ['badge-inbox'],
    queryFn: () => api.get<{ unreadCount: number }>('/messages/unread-count'),
    enabled: kind === 'inbox',
    refetchInterval: 60_000,
  })
  const notif = useQuery({
    queryKey: ['badge-notif'],
    queryFn: () => api.get<{ unreadCount: number }>('/notifications/unread-count'),
    enabled: kind === 'notif',
    refetchInterval: 60_000,
  })
  if (kind === 'inbox') return (inbox.data?.unreadCount ?? 0) > 0 ? inbox.data!.unreadCount : null
  if (kind === 'notif') return (notif.data?.unreadCount ?? 0) > 0 ? notif.data!.unreadCount : null
  return null
}

function NavBadge({ kind }: { kind: string }) {
  const count = useBadge(kind)
  if (count === null) return null
  return (
    <span className="ml-auto min-w-[20px] px-1.5 py-0.5 text-micro font-semibold text-white bg-accent-500 rounded-full text-center tabular-nums">
      {count > 99 ? '99+' : count}
    </span>
  )
}

function SidebarContent({ onNavigate, identity, onLogout }: { onNavigate: () => void; identity: { name: string; role: string } | null; onLogout: () => void }) {
  const location = useLocation()
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const displayName = identity?.name || 'Demo User'
  const roleLabel = identity ? ROLE_LABELS[identity.role as Role] ?? identity.role : ''
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center gap-2.5 px-4">
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] shadow-card">
          <Bot className="h-5 w-5 text-white" aria-hidden="true" />
        </div>
        <div className="leading-tight">
          <p className="font-display font-700 font-bold text-[17px] text-campus-950 tracking-tight">Campus AI</p>
          <p className="text-micro text-surface-400">MIT Mysore</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-5 scrollbar-light" aria-label="Primary">
        {NAV_GROUPS.map(group => (
          <div key={group.label}>
            <p className="px-3 mb-1.5 text-micro font-semibold text-surface-400 tracking-wide">{group.label}</p>
            <div className="space-y-0.5">
              {group.items.map(item => {
                const isActive = location.pathname === item.path || (item.path === '/home' && location.pathname === '/')
                return (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    onClick={onNavigate}
                    aria-current={isActive ? 'page' : undefined}
                    className={cn(
                      'group relative flex items-center gap-3 px-4 py-2 text-body-sm font-medium transition-colors duration-150',
                      'rounded-[20px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-300',
                      isActive
                        ? 'text-campus-600 bg-campus-50'
                        : 'text-surface-500 hover:text-campus-600 hover:bg-surface-100'
                    )}
                  >
                    <item.icon
                      className={cn('h-[18px] w-[18px] flex-shrink-0 transition-colors', isActive ? 'text-campus-600' : 'text-surface-400 group-hover:text-campus-500')}
                      aria-hidden="true"
                    />
                    <span>{item.label}</span>
                    {item.badge && <NavBadge kind={item.badge} />}
                  </NavLink>
                )
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="p-3">
        <div className="rounded-2xl bg-surface-100 border border-campus-900/[0.06] p-3">
          <div className="relative">
            <button
              className="w-full flex items-center gap-2.5 rounded-xl px-1 py-1 text-body-sm transition-colors hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-300"
              onClick={() => setUserMenuOpen(!userMenuOpen)}
              aria-expanded={userMenuOpen}
              aria-haspopup="true"
            >
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold text-body-sm flex-shrink-0" aria-hidden="true">
                {displayName.charAt(0).toUpperCase()}
              </div>
              <div className="flex-1 text-left min-w-0">
                <p className="font-medium text-campus-950 truncate">{displayName}</p>
                <p className="text-micro text-surface-400 truncate">{roleLabel ? `${roleLabel} · ` : ''}MIT Mysore</p>
              </div>
              <ChevronDown className={cn('h-4 w-4 text-surface-400 transition-transform', userMenuOpen && 'rotate-180')} />
            </button>
            {userMenuOpen && (
              <div className="absolute bottom-full left-0 right-0 mb-2 animate-slide-up">
                <div className="bg-white rounded-xl border border-campus-900/10 shadow-elevated p-1">
                  <button
                    onClick={onLogout}
                    className="w-full flex items-center gap-2.5 rounded-lg px-3 py-2 text-body-sm text-surface-600 hover:bg-surface-100 hover:text-campus-950"
                  >
                    <LogOut className="h-4 w-4" aria-hidden="true" />
                    Sign out
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [identity, setIdentityState] = useState<{ name: string; role: string } | null>(null)
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    const name = getName()
    const role = getRole()
    if (name || role) setIdentityState({ name: name ?? '', role: role ?? 'student' })
    else setIdentityState(null)
    setSidebarOpen(false)
  }, [location.pathname])

  const handleLogout = () => {
    clearIdentity()
    setIdentityState(null)
    navigate('/login')
  }

  const meta = TITLES[location.pathname] ?? { title: 'Campus AI', sub: '' }

  return (
    <div className="min-h-screen bg-[#f5f7fa]">
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 bg-campus-950/40 backdrop-blur-[2px] lg:hidden" onClick={() => setSidebarOpen(false)} aria-hidden="true" />
      )}

      <aside
        className={cn(
          'fixed left-0 top-0 z-50 h-full w-[248px] bg-white shadow-card border-r border-campus-900/[0.06]',
          'transform transition-transform duration-200 ease-out lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )}
        aria-label="Main navigation"
      >
        <div className="absolute top-0 right-0 p-2 lg:hidden">
          <button className="p-2 rounded-lg text-surface-400 hover:bg-surface-100" onClick={() => setSidebarOpen(false)} aria-label="Close sidebar">
            <X className="h-5 w-5" />
          </button>
        </div>
        <SidebarContent onNavigate={() => setSidebarOpen(false)} identity={identity} onLogout={handleLogout} />
      </aside>

      <div className="lg:pl-[248px] min-h-screen flex flex-col">
        <header className="sticky top-0 z-30 border-b border-campus-900/[0.06] bg-[#f5f7fa]/85 backdrop-blur-md">
          <div className="flex h-16 items-center gap-3 px-4 lg:px-8">
            <button
              className="lg:hidden p-2 -ml-2 rounded-xl text-surface-600 hover:bg-campus-900/[0.05]"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="min-w-0">
              <h1 className="font-display font-bold text-[19px] leading-tight text-campus-950 tracking-tight truncate">{meta.title}</h1>
              {meta.sub && <p className="text-caption text-surface-500 truncate hidden sm:block">{meta.sub}</p>}
            </div>
            <div className="ml-auto flex items-center gap-2">
              <span className="hidden md:inline-flex items-center gap-1.5 rounded-full border border-campus-900/10 bg-white/70 px-3 py-1.5 text-caption text-surface-600">
                <Sparkles className="h-3.5 w-3.5 text-accent-500" aria-hidden="true" />
                {identity ? `Signed in as ${identity.name || `#${getId()}`} (${identity.role})` : 'Demo mode'}
              </span>
            </div>
          </div>
        </header>

        <main className="flex-1 px-4 py-6 lg:px-8 lg:py-8 animate-fade-in" id="main-content" role="main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
