import { useState, useEffect } from 'react'
import { Outlet, NavLink, Link, useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { cn } from '../utils/cn'
import { getName, getRole, getId, clearIdentity, ROLE_LABELS, type Role } from '../utils/identity'
import { navGroupsForRole, breadcrumbFor, type NavItem } from '../config/navigation'
import { LogOut, Menu, X, ChevronDown, Sparkles } from 'lucide-react'

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

function NavRow({ item, onNavigate }: { item: NavItem; onNavigate: () => void }) {
  const location = useLocation()
  const isActive = location.pathname === item.path
  return (
    <NavLink
      to={item.path}
      onClick={onNavigate}
      aria-current={isActive ? 'page' : undefined}
      className={cn(
        'group relative flex items-center gap-3 px-3.5 py-2 text-body-sm font-medium transition-colors duration-150',
        'rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-300',
        isActive
          ? 'text-campus-700 bg-campus-50 font-semibold'
          : 'text-surface-500 hover:text-campus-700 hover:bg-surface-100'
      )}
    >
      {/* Active marker: a small bar rather than a colour change alone, so the
          current page is legible without relying on hue. */}
      <span
        className={cn(
          'absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-accent-500 transition-opacity',
          isActive ? 'opacity-100' : 'opacity-0'
        )}
        aria-hidden="true"
      />
      <item.icon
        className={cn('h-[18px] w-[18px] flex-shrink-0', isActive ? 'text-campus-600' : 'text-surface-400 group-hover:text-campus-500')}
        aria-hidden="true"
      />
      <span className="truncate">{item.label}</span>
      {item.badge && <NavBadge kind={item.badge} />}
    </NavLink>
  )
}

function SidebarContent({
  onNavigate,
  identity,
  onLogout,
}: {
  onNavigate: () => void
  identity: { name: string; role: Role } | null
  onLogout: () => void
}) {
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const displayName = identity?.name || 'Demo user'
  const roleLabel = identity ? ROLE_LABELS[identity.role] : ''
  const groups = navGroupsForRole(identity?.role ?? null)

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center gap-2.5 px-4">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] shadow-card">
          <Sparkles className="h-[18px] w-[18px] text-white" aria-hidden="true" />
        </div>
        <div className="leading-tight">
          <p className="font-display font-bold text-[17px] text-campus-950 tracking-tight">Campus AI</p>
          <p className="text-micro text-surface-400">MIT Mysore</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-5 scrollbar-light" aria-label="Primary">
        {groups.map((group) => (
          <div key={group.label}>
            <p className="px-3.5 mb-1.5 text-micro font-semibold text-surface-400 tracking-wide">{group.label}</p>
            <div className="space-y-0.5">
              {group.items.map((item) => (
                <NavRow key={`${group.label}-${item.path}`} item={item} onNavigate={onNavigate} />
              ))}
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
              <div
                className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold text-body-sm flex-shrink-0"
                aria-hidden="true"
              >
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
  const [identity, setIdentityState] = useState<{ name: string; role: Role } | null>(null)
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

  const trail = breadcrumbFor(location.pathname)

  return (
    <div className="min-h-screen bg-[#f5f7fa]">
      {/* First thing in the tab order. Without it a keyboard user walks all 23
          nav links before reaching the content on every single page. */}
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[60] focus:rounded-xl focus:bg-white focus:px-4 focus:py-2.5 focus:text-body-sm focus:font-semibold focus:shadow-elevated"
      >
        Skip to content
      </a>

      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-campus-950/40 backdrop-blur-[2px] lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
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
          <button
            className="p-2 rounded-lg text-surface-400 hover:bg-surface-100"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close sidebar"
          >
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
            <nav aria-label="Breadcrumb" className="min-w-0">
              <ol className="flex items-center gap-1.5 text-body-sm">
                {trail.map((crumb, i) => {
                  const last = i === trail.length - 1
                  return (
                    <li key={`${crumb.label}-${i}`} className="flex items-center gap-1.5 min-w-0">
                      {i > 0 && (
                        <span className="text-surface-300 select-none" aria-hidden="true">/</span>
                      )}
                      {crumb.to && !last ? (
                        <Link
                          to={crumb.to}
                          className="text-surface-500 hover:text-campus-700 truncate transition-colors"
                        >
                          {crumb.label}
                        </Link>
                      ) : (
                        <span
                          className={cn(
                            'truncate',
                            last ? 'font-display font-semibold text-campus-950' : 'text-surface-500'
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
            <div className="ml-auto flex items-center gap-2">
              {identity && (
                <span className="hidden sm:inline-flex items-center gap-2 rounded-full border border-campus-900/[0.07] bg-white/70 pl-1 pr-3 py-1">
                  <span
                    className="flex h-6 w-6 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white text-micro font-bold"
                    aria-hidden="true"
                  >
                    {(identity.name || '?').charAt(0).toUpperCase()}
                  </span>
                  <span className="text-caption text-campus-950 font-medium max-w-[14ch] truncate">
                    {identity.name || `#${getId()}`}
                  </span>
                  <span className="text-micro font-semibold text-surface-400">
                    {ROLE_LABELS[identity.role]}
                  </span>
                </span>
              )}
            </div>
          </div>
        </header>

        {/* tabIndex={-1} so the skip link moves focus here, not just the scroll. */}
        <main className="flex-1 px-4 py-6 lg:px-8 lg:py-8 animate-fade-in" id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
