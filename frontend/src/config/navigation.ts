import {
  FileText,
  FolderKanban,
  Calendar,
  User,
  Settings,
  Bot,
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

const ALL: Role[] = ['student', 'alumni', 'recruiter']

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
 * Note: admin/evaluation access is gated on the *id* allow-list
 * (`campus.admin-ids` in application.properties), not on role — so those items
 * are shown to students, who are the only population that can hold that id.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Start',
    items: [
      { path: '/home', label: 'Home', icon: LayoutDashboard, hint: 'Your campus at a glance' },
      { path: '/chat', label: 'Ask AI', icon: Bot, hint: 'Grounded answers from campus documents' },
      { path: '/search', label: 'Search', icon: Search, hint: 'People, projects, events, documents' },
    ],
  },
  {
    label: 'Your space',
    items: [
      { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: ['student'], hint: 'Your stats and activity' },
      { path: '/dashboard/alumni', label: 'Dashboard', icon: LayoutDashboard, roles: ['alumni'], hint: 'Mentorship and referrals' },
      { path: '/dashboard/recruiter', label: 'Dashboard', icon: LayoutDashboard, roles: ['recruiter'], hint: 'Postings and applications' },
      { path: '/profile', label: 'Profile', icon: User, hint: 'Your campus identity' },
      { path: '/messages', label: 'Messages', icon: Inbox, badge: 'inbox', hint: 'Direct lines to your peers' },
      { path: '/notifications', label: 'Notifications', icon: Bell, badge: 'notif', hint: 'Never miss what matters' },
    ],
  },
  {
    label: 'Campus life',
    items: [
      { path: '/community', label: 'Community', icon: Users, hint: 'Conversations across campus' },
      { path: '/projects', label: 'Projects', icon: FolderKanban, hint: 'Build together, ship together' },
      { path: '/events', label: 'Events', icon: Calendar, hint: 'Hackathons, workshops, gatherings' },
    ],
  },
  {
    label: 'Grow',
    items: [
      { path: '/mentorship', label: 'Mentorship', icon: GraduationCap, hint: 'Learn from those who walked it' },
      { path: '/roadmaps', label: 'Roadmaps', icon: Map, hint: 'Alumni guides to your target role' },
      { path: '/referrals', label: 'Referrals', icon: Briefcase, hint: 'Job referrals from alumni who opt in' },
      { path: '/skills-graph', label: 'Skill Graph', icon: Network, roles: ['student'], hint: 'How your skills connect' },
      { path: '/reviews', label: 'Reviews', icon: Award, hint: 'Peer reputation and trust' },
    ],
  },
  {
    label: 'Careers & knowledge',
    items: [
      { path: '/recruiter', label: 'Careers', icon: Briefcase, roles: ['student', 'alumni'], hint: 'Roles and your applications' },
      { path: '/documents', label: 'Library', icon: FileText, hint: 'Curated knowledge, ready to ask about' },
    ],
  },
  {
    label: 'Manage',
    items: [
      { path: '/analytics', label: 'Analytics', icon: BarChart3, hint: 'The pulse of your campus' },
      { path: '/eval', label: 'Evaluation', icon: FlaskConical, roles: ['student'], hint: 'RAG and teammate research harness' },
      { path: '/admin', label: 'Admin', icon: ShieldAlert, roles: ['student'], hint: 'Keep the community healthy' },
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
  return meta ? [{ label: 'Campus AI' }, { label: meta.title }] : [{ label: 'Campus AI' }]
}
