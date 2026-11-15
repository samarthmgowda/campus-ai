import { useState } from 'react'
import { Outlet, NavLink, Link, useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { cn } from '../utils/cn'
import { useSession, resetSession, ROLE_LABELS, type Role } from '../utils/identity'
import { navGroupsForRole, breadcrumbFor, type NavItem } from '../config/navigation'
import { LogOut, Menu, X, ChevronDown, Sparkles, Bell, Inbox, User as UserIcon, Settings as SettingsIcon } from 'lucide-react'
import ChatWidget from './ChatWidget'

function useBadge(kind: string | null, account: string | null): number | null {
  // Scoped to the account even though the cache is cleared on switch: a key
  // that names the user cannot be wrong, and the badge must never show one
  // account's unread count on another's shell.
  const inbox = useQuery({
    queryKey: ['badge-inbox', account],
    queryFn: () => api.get<{ unreadCount: number }>('/messages/unread-count'),
    enabled: kind === 'inbox' && !!account,
    refetchInterval: 60_000,
  })
  const notif = useQuery({
    queryKey: ['badge-notif', account],
    queryFn: () => api.get<{ unreadCount: number }>('/notifications/unread-count'),
    enabled: kind === 'notif' && !!account,
    refetchInterval: 60_000,
  })
  if (kind === 'inbox') return (inbox.data?.unreadCount ?? 0) > 0 ? inbox.data!.unreadCount : null
  if (kind === 'notif') return (notif.data?.unreadCount ?? 0) > 0 ? notif.data!.unreadCount : null
  return null
}

function NavBadge({ kind, account }: { kind: string; account: string | null }) {
  const count = useBadge(kind, account)
  if (count === null) return null
  return (
    <span className="ml-auto min-w-[20px] px-1.5 py-0.5 text-micro font-semibold text-white bg-er-500 rounded-full text-center tabular-nums">
      {count > 99 ? '99+' : count}
    </span>
  )
}

/** The unread dot on the header icons — only rendered when there is something. */
function UnreadDot({ kind, account }: { kind: string; account: string | null }) {
  const count = useBadge(kind, account)
  if (count === null) return null
  // Coloured on the sidebar's own canvas, so the ring matches the shell rather
  // than the white ring the old light theme used.
  return (
    <span
      className={cn(
        'absolute right-1 top-1 h-2 w-2 rounded-full border-2 border-er-950',
        kind === 'inbox' ? 'bg-er-400' : 'bg-orange-500'
      )}
      aria-hidden="true"
    />
  )
}

function NavRow({ item, onNavigate, account }: { item: NavItem; onNavigate: () => void; account: string | null }) {
  const location = useLocation()
  const isActive = location.pathname === item.path
  return (
    <NavLink
      to={item.path}
      onClick={onNavigate}
      aria-current={isActive ? 'page' : undefined}
      className={cn(
        'group relative flex items-center gap-3 px-3.5 py-2 text-body-sm font-medium transition-colors duration-150',
        'rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500',
        isActive
          ? 'bg-er-500/12 text-ink-50 font-semibold'
          : 'text-ink-400 hover:bg-white/[0.05] hover:text-ink-100'
      )}
    >
      {/* Active marker: a small bar rather than a colour change alone, so the
          current page is legible without relying on hue. */}
      <span
        className={cn(
          'absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-er-500 transition-opacity',
          isActive ? 'opacity-100' : 'opacity-0'
        )}
        aria-hidden="true"
      />
      <item.icon
        className={cn(
          'h-[18px] w-[18px] flex-shrink-0 transition-colors',
          isActive ? 'text-er-300' : 'text-ink-500 group-hover:text-er-400'
        )}
        aria-hidden="true"
      />
      <span className="truncate">{item.label}</span>
      {item.badge && <NavBadge kind={item.badge} account={account} />}
    </NavLink>
  )
}

function SidebarContent({
  onNavigate,
  identity,
  account,
  onLogout,
}: {
  onNavigate: () => void
  identity: { name: string | null; role: Role } | null
  account: string | null
  onLogout: () => void
}) {
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const displayName = identity?.name || 'Campus member'
  const roleLabel = identity ? ROLE_LABELS[identity.role] : ''
  const groups = navGroupsForRole(identity?.role ?? null)

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center gap-2.5 px-4">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-er-500 to-er-600 shadow-glow">
          <Sparkles className="h-[18px] w-[18px] text-white" aria-hidden="true" />
        </div>
        <div className="leading-tight">
          <p className="font-display font-bold text-[17px] text-ink-50 tracking-tight">Campus Connect</p>
          <p className="text-micro text-ink-500">MIT Mysore</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-5 scrollbar-light" aria-label="Primary">
        {groups.map((group) => (
          <div key={group.label}>
            <p className="px-3.5 mb-1.5 text-micro font-semibold uppercase tracking-wider text-ink-500">{group.label}</p>
            <div className="space-y-0.5">
              {group.items.map((item) => (
                <NavRow
                  key={`${group.label}-${item.path}`}
                  item={item}
                  onNavigate={onNavigate}
                  account={account}
                />
              ))}
            </div>
          </div>
        ))}
      </nav>

      <div className="p-3">
        <div className="rounded-2xl bg-white/[0.04] border border-white/[0.07] p-2">
          <div className="relative">
            <button
              className="w-full flex items-center gap-2.5 rounded-xl px-2 py-2 text-body-sm transition-colors hover:bg-white/[0.06] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
              onClick={() => setUserMenuOpen(!userMenuOpen)}
              aria-expanded={userMenuOpen}
              aria-haspopup="true"
            >
              <div
                className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-er-500 to-er-600 text-white font-display font-bold text-body-sm flex-shrink-0"
                aria-hidden="true"
              >
                {displayName.charAt(0).toUpperCase()}
              </div>
              <div className="flex-1 text-left min-w-0">
                <p className="font-medium text-ink-100 truncate">{displayName}</p>
                <p className="text-micro text-ink-500 truncate">{roleLabel ? `${roleLabel} · ` : ''}MIT Mysore</p>
              </div>
              <ChevronDown className={cn('h-4 w-4 text-ink-500 transition-transform', userMenuOpen && 'rotate-180')} />
            </button>
            {userMenuOpen && (
              <div className="absolute bottom-full left-0 right-0 mb-2 animate-slide-up">
                <div className="bg-er-800 rounded-xl border border-white/10 shadow-elevated p-1">
                  {[
                    { to: '/profile', icon: UserIcon, label: 'View & edit profile' },
                    { to: '/settings', icon: SettingsIcon, label: 'Settings' },
                  ].map(({ to, icon: Icon, label }) => (
                    <NavLink
                      key={to}
                      to={to}
                      onClick={onNavigate}
                      className="w-full flex items-center gap-2.5 rounded-lg px-3 py-2 text-body-sm text-ink-300 hover:bg-white/[0.06] hover:text-ink-50"
                    >
                      <Icon className="h-4 w-4" aria-hidden="true" />
                      {label}
                    </NavLink>
                  ))}
                  <button
                    onClick={onLogout}
                    className="w-full flex items-center gap-2.5 rounded-lg px-3 py-2 text-body-sm text-ink-300 hover:bg-red-500/12 hover:text-red-300"
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

function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  // Reactive: the shell follows the live session, so a sign-out (or a token
  // dying) updates the name, role and nav in the same paint rather than
  // leaving the previous account's identity on screen.
  const session = useSession()
  const identity = session ? { name: session.name, role: session.role } : null

  // Close the mobile drawer whenever the route changes.
  const [lastPath, setLastPath] = useState(location.pathname)
  if (location.pathname !== lastPath) {
    setLastPath(location.pathname)
    if (sidebarOpen) setSidebarOpen(false)
  }

  const handleLogout = () => {
    // resetSession clears the query cache and every user-scoped store before
    // dropping the token, so the next account cannot inherit this one's data.
    resetSession('logout')
    navigate('/login', { replace: true })
  }

  const trail = breadcrumbFor(location.pathname)

  return (
    <div className="min-h-screen bg-er-950">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[60] focus:rounded-xl focus:bg-er-500 focus:px-4 focus:py-2.5 focus:text-white focus:font-semibold focus:shadow-elevated"
      >
        Skip to content
      </a>

      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-[2px] lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          'fixed left-0 top-0 z-50 h-full w-[248px] bg-er-950 border-r border-white/[0.07]',
          'transform transition-transform duration-200 ease-out lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )}
        aria-label="Main navigation"
      >
        <div className="absolute top-0 right-0 p-2 lg:hidden">
          <button
            className="p-2 rounded-lg text-ink-400 hover:bg-white/[0.06] hover:text-ink-100"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close sidebar"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <SidebarContent
          onNavigate={() => setSidebarOpen(false)}
          identity={identity}
          account={session?.userId ?? null}
          onLogout={handleLogout}
        />
      </aside>

      <div className="lg:pl-[248px] min-h-screen flex flex-col">
        <header className="sticky top-0 z-30 border-b border-white/[0.07] bg-er-950/85 backdrop-blur-md">
          <div className="flex h-16 items-center gap-3 px-4 lg:px-8">
            <button
              className="lg:hidden p-2 -ml-2 rounded-xl text-ink-300 hover:bg-white/[0.06] hover:text-ink-50"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <nav aria-label="Breadcrumb" className="min-w-0">
              <ol className="flex items-center gap-1.5 text-body-sm">
                {trail.map((crumb, i) => {
                  const last = i === trail.length - 1
                  return (
                    <li key={`${crumb.label}-${i}`} className="flex items-center gap-1.5 min-w-0">
                      {i > 0 && (
                        <span className="text-ink-600 select-none" aria-hidden="true">/</span>
                      )}
                      {crumb.to && !last ? (
                        <Link
                          to={crumb.to}
                          className="text-ink-400 hover:text-er-300 truncate transition-colors"
                        >
                          {crumb.label}
                        </Link>
                      ) : (
                        <span
                          className={cn(
                            'truncate',
                            last ? 'font-display font-semibold text-ink-50' : 'text-ink-400'
                          )}
                          aria-current={last ? 'page' : undefined}
                        >
                          {crumb.label}
                        </span>
                      )}
                    </li>
                  )
                })}
              </ol>
            </nav>
            <div className="ml-auto flex items-center gap-1">
              <button
                className="relative p-2 rounded-xl text-ink-400 hover:bg-white/[0.06] hover:text-ink-100 transition-colors"
                onClick={() => navigate('/notifications')}
                aria-label="Notifications"
              >
                <Bell className="h-5 w-5" />
                <UnreadDot kind="notif" account={session?.userId ?? null} />
              </button>
              <button
                className="relative p-2 rounded-xl text-ink-400 hover:bg-white/[0.06] hover:text-ink-100 transition-colors"
                onClick={() => navigate('/messages')}
                aria-label="Messages"
              >
                <Inbox className="h-5 w-5" />
                <UnreadDot kind="inbox" account={session?.userId ?? null} />
              </button>
            </div>
          </div>
        </header>

        <main className="flex-1 px-4 py-6 lg:px-8 lg:py-8 animate-fade-in" id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
        <ChatWidget />
      </div>

    </div>
  )
}

export default Layout
