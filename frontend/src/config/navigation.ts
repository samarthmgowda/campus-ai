import {
  FileText,
  FolderKanban,
  Calendar,
  Settings,
  Users,
  Briefcase,
  ShieldAlert,
  BarChart3,
  FlaskConical,
  LayoutDashboard,
  Search,
  Network,
  type LucideIcon,
} from 'lucide-react'
import type { Role } from '../utils/identity'

export interface NavItem {
  path: string
  label: string
  icon: LucideIcon
  /** Which signed-in roles may see this destination. Omitted = every role. */
  roles?: Role[]
  /** Which unread counter to surface as a badge, if any. */
  badge?: 'inbox' | 'notif'
  /** Longer description, used by the command palette and empty states. */
  hint?: string
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

const ALL: Role[] = ['student', 'alumni', 'recruiter', 'admin']

/**
 * The single source of truth for what appears in the sidebar.
 *
 * Before this, one flat list was rendered to every role, so an alumni saw the
 * student dashboard and a student saw the recruiter hub. Destinations now
 * declare the roles they belong to, and the sidebar filters on the signed-in
 * role.
 *
 * `Dashboard` is deliberately listed once per role with a distinct path rather
 * than as a single item, because the student, alumni and recruiter dashboards
 * are genuinely different screens.
 *
 * Admin/evaluation access is gated on the real ADMIN role — the backend checks
 * the JWT principal's role, and `RouteGuards.RequireRole` (reading `getRole()`
 * from the session snapshot) keeps those destinations to admins only.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Start',
    items: [
      { path: '/home', label: 'Home', icon: LayoutDashboard, hint: 'Your campus at a glance' },
      { path: '/search', label: 'Search', icon: Search, hint: 'People, projects, events, documents' },
    ],
  },
  {
    label: 'Your space',
    items: [
      { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: ['student'], hint: 'Your stats and activity' },
      { path: '/dashboard/alumni', label: 'Dashboard', icon: LayoutDashboard, roles: ['alumni'], hint: 'Mentorship and referrals' },
      { path: '/dashboard/recruiter', label: 'Dashboard', icon: LayoutDashboard, roles: ['recruiter'], hint: 'Postings and applications' },
    ],
  },
  {
    label: 'Campus life',
    items: [
      { path: '/community', label: 'Community', icon: Users, hint: 'Conversations across campus' },
      { path: '/projects', label: 'Projects', icon: FolderKanban, hint: 'Build together, ship together' },
      { path: '/events', label: 'Events', icon: Calendar, hint: 'Hackathons, workshops, gatherings' },
      { path: '/connect', label: 'Connect', icon: Network, roles: ['student', 'alumni', 'recruiter'], hint: 'Network, Grow and Mentorship' },
      { path: '/team-requests', label: 'Team Requests', icon: Users, roles: ['student', 'alumni', 'recruiter'], hint: 'Find teammates for hackathons and events' },
    ],
  },
  {
    label: 'Placement & Training',
    items: [
      { path: '/placement-training', label: 'Placement & Training', icon: Briefcase, roles: ['student', 'alumni'], hint: 'Job openings and career roadmaps' },
    ],
  },
  {
    label: 'Manage',
    items: [
      { path: '/documents', label: 'Library', icon: FileText, roles: ['admin'], hint: 'Curated knowledge and RAG sources' },
      { path: '/analytics', label: 'Analytics', icon: BarChart3, roles: ['admin'], hint: 'The pulse of your campus' },
      { path: '/eval', label: 'Evaluation', icon: FlaskConical, roles: ['admin'], hint: 'RAG research harness' },
      { path: '/admin', label: 'Admin', icon: ShieldAlert, roles: ['admin'], hint: 'Keep the community healthy' },
      { path: '/settings', label: 'Settings', icon: Settings, hint: 'Make it yours' },
    ],
  },
]

/** Every nav item the given role is allowed to see, flattened. */
export function navItemsForRole(role: Role | null): NavItem[] {
  const effective: Role = role ?? 'student'
  return NAV_GROUPS.flatMap((g) => g.items).filter(
    (item) => !item.roles || item.roles.includes(effective)
  )
}

/** The nav groups for a role, with empty groups removed. */
export function navGroupsForRole(role: Role | null): NavGroup[] {
  const effective: Role = role ?? 'student'
  return NAV_GROUPS.map((g) => ({
    ...g,
    items: g.items.filter((item) => !item.roles || item.roles.includes(effective)),
  })).filter((g) => g.items.length > 0)
}

/** Page title/subtitle per route. Consumed by breadcrumbFor, and available as
 *  a fallback for any page that does not render its own PageHeader. */
export const PAGE_META: Record<string, { title: string; sub: string }> = {
  '/home': { title: 'Home', sub: 'Everything happening, in one glance' },
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
  '/profiles': { title: 'Member', sub: 'Campus profile' },
  '/mentorship': { title: 'Mentorship', sub: 'Learn from those who walked it' },
  '/skills-graph': { title: 'Skill Graph', sub: 'How your skills connect' },
  '/recruiter': { title: 'Careers', sub: 'Roles and your applications' },
  '/dashboard': { title: 'Dashboard', sub: 'Your Campus Connect overview' },
  '/dashboard/alumni': { title: 'Dashboard', sub: 'Mentorship and referrals' },
  '/dashboard/recruiter': { title: 'Dashboard', sub: 'Postings and applications' },
  '/analytics': { title: 'Analytics', sub: 'The pulse of your campus' },
  '/eval': { title: 'Evaluation', sub: 'RAG and teammate research harness' },
  '/admin': { title: 'Admin', sub: 'Keep the community healthy' },
  '/profile': { title: 'Profile', sub: 'Your campus identity' },
  '/settings': { title: 'Settings', sub: 'Make it yours' },
}

export { ALL as ALL_ROLES }

/**
 * Where a route sits in the nav tree, as a trail of crumbs.
 *
 * The shell header used to print `PAGE_META[path].title` while the page body
 * printed the same title again in its own PageHeader — the name of the page
 * appeared twice, a few centimetres apart. The shell now shows the *location*
 * (`Start / Home`) and the page owns its own headline.
 */
export function breadcrumbFor(path: string): { label: string; to?: string }[] {
  for (const group of NAV_GROUPS) {
    const item = group.items.find((i) => i.path === path)
    if (item) {
      return [
        { label: group.label },
        { label: item.label, to: item.path },
      ]
    }
  }
  // Routes that live outside the sidebar (or a typo) still deserve a name.
  const meta = PAGE_META[path]
  return meta ? [{ label: 'Campus Connect' }, { label: meta.title }] : [{ label: 'Campus Connect' }]
}
