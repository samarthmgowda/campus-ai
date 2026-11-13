import { Link } from 'react-router-dom'
import { cn } from '../utils/cn'
import { useInView } from '../hooks/useInView'
import { useReducedMotion } from '../hooks/useReducedMotion'
import PeerMatchDemo from '../components/landing/PeerMatchDemo'
import {
  Sparkles,
  ArrowRight,
  Users,
  CalendarDays,
  FolderKanban,
  GraduationCap,
  ShieldCheck,
  UserCheck,
  MessagesSquare,
  Lock,
  type LucideIcon,
} from 'lucide-react'

/* ------------------------------------------------------------------ */
/* Colour discipline                                                   */
/*                                                                     */
/* Midnight black (#090A10) is the page. Electric blue (#2F6BFF) is    */
/* every accent, border, icon and link. Sunset orange (#FF7A1A) appears */
/* on exactly one class of element: a button the visitor should press. */
/* Never a gradient, never decoration, never body text.                */
/* ------------------------------------------------------------------ */

/** The only sanctioned orange treatment: a primary call to action. */
const CTA =
  'inline-flex items-center justify-center gap-2 rounded-xl bg-er-orange px-6 py-3 text-body font-semibold text-er-950 shadow-[0_4px_20px_rgba(255,122,26,0.28)] transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-orange focus-visible:ring-offset-2 focus-visible:ring-offset-er-950'

/** Secondary action: blue, quieter, never competes with the orange. */
const CTA_GHOST =
  'inline-flex items-center justify-center gap-2 rounded-xl border border-er-600/50 bg-er-900 px-6 py-3 text-body font-semibold text-ink-100 transition-colors hover:border-er-500/70 hover:bg-er-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500'

function Reveal({
  children,
  className,
  delay = 0,
}: {
  children: React.ReactNode
  className?: string
  delay?: number
}) {
  const { ref, inView } = useInView()
  const reduced = useReducedMotion()
  return (
    <div
      ref={ref}
      className={cn(
        reduced ? '' : 'transition-all duration-700 ease-out-expo',
        !reduced && (inView ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-6'),
        className
      )}
      style={!reduced && delay ? { transitionDelay: `${delay}ms` } : undefined}
    >
      {children}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Content                                                             */
/* ------------------------------------------------------------------ */

const CAPABILITIES: { icon: LucideIcon; title: string; body: string }[] = [
  {
    icon: UserCheck,
    title: 'Find your people',
    body: 'Match with students who share your interests, your stack, and your ambition — filtered to people actually on your campus.',
  },
  {
    icon: FolderKanban,
    title: 'Build a real team',
    body: 'Post what you are building and the role you are missing. Get ranked candidates instead of a group chat full of maybes.',
  },
  {
    icon: MessagesSquare,
    title: 'Talk, then meet',
    body: 'Direct messages, project pages and a campus feed keep the conversation in one place instead of scattered across five apps.',
  },
  {
    icon: CalendarDays,
    title: 'Catch what matters',
    body: 'Hackathons, workshops and drives in one feed, so a team does not miss the deadline it was formed for.',
  },
]

const STEPS = [
  {
    title: 'Join with your college account',
    body: 'No open sign-up. Your profile is tied to MIT Mysore, so everyone you meet is a verified member of your campus.',
  },
  {
    title: 'Say what you are looking for',
    body: 'A hackathon team, a project collaborator, a study group, or simply people in your domain. Set skills and interests once.',
  },
  {
    title: 'Connect and build',
    body: 'Get matched peers, message them, form the team, and keep the project page alive through the sprint.',
  },
]

const AUDIENCES: {
  icon: LucideIcon
  role: string
  line: string
  points: string[]
}[] = [
  {
    icon: GraduationCap,
    role: 'Students',
    line: 'Find the team before the deadline finds you.',
    points: [
      'Get matched for hackathons and projects',
      'Find peers in your domain and interest area',
      'Build a profile that gets you picked',
      'Keep every team and event in one feed',
    ],
  },
  {
    icon: Users,
    role: 'Alumni',
    line: 'Stay reachable to the campus you came from.',
    points: [
      'Mentor students in your field',
      'Share the roadmap you wish you had',
      'Open referrals where you work',
      'Keep a line to your own batch',
    ],
  },
  {
    icon: ShieldCheck,
    role: 'Faculty & admin',
    line: 'A closed network you can actually vouch for.',
    points: [
      'Membership restricted to the college',
      'Moderation tools for reports and posts',
      'Visibility into what is happening on campus',
      'No public exposure of student data',
    ],
  },
]

/* ------------------------------------------------------------------ */
/* Page                                                                */
/* ------------------------------------------------------------------ */

export default function LandingPage() {
  const reduced = useReducedMotion()

  return (
    <div className="min-h-dvh bg-er-950 text-ink-50">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-xl focus:bg-er-orange focus:px-4 focus:py-2.5 focus:text-body-sm focus:font-semibold focus:text-er-950"
      >
        Skip to content
      </a>

      {/* ---------------------------------------------------------- Header */}
      <header className="sticky top-0 z-40 border-b border-er-800/70 bg-er-950/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-5">
          <Link
            to="/"
            className="flex items-center gap-2.5 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
          >
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-er-500">
              <Sparkles className="h-[18px] w-[18px] text-er-950" aria-hidden="true" />
            </span>
            <span className="font-display text-[17px] font-bold tracking-tight text-ink-50">
              Campus Connect
            </span>
          </Link>

          <nav className="ml-6 hidden items-center gap-1 md:flex" aria-label="Page sections">
            {[
              { href: '#how', label: 'How it works' },
              { href: '#what', label: 'What you can do' },
              { href: '#who', label: 'Who it is for' },
            ].map((l) => (
              <a
                key={l.href}
                href={l.href}
                className="rounded-lg px-3 py-2 text-body-sm font-medium text-ink-300 transition-colors hover:bg-er-900 hover:text-ink-50"
              >
                {l.label}
              </a>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <Link
              to="/login"
              className="rounded-xl px-3.5 py-2 text-body-sm font-medium text-ink-300 transition-colors hover:bg-er-900 hover:text-ink-50"
            >
              Sign in
            </Link>
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 rounded-xl bg-er-orange px-4 py-2 text-body-sm font-semibold text-er-950 transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-orange focus-visible:ring-offset-2 focus-visible:ring-offset-er-950"
            >
              Enter campus
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
          </div>
        </div>
      </header>

      <main id="main">
        {/* -------------------------------------------------------- Hero */}
        <section className="relative overflow-hidden">
          {/* Decorative field. Blue only — orange is reserved for buttons,
              so it never appears as an atmospheric wash. */}
          <div className="pointer-events-none absolute inset-0" aria-hidden="true">
            <div
              className={cn(
                'absolute -top-40 right-[-10%] h-[520px] w-[520px] rounded-full opacity-[0.18] blur-[100px]',
                'bg-[radial-gradient(circle_at_center,#2F6BFF,transparent_70%)]',
                !reduced && 'animate-drift'
              )}
            />
            <div
              className={cn(
                'absolute -bottom-56 left-[-12%] h-[460px] w-[460px] rounded-full opacity-[0.12] blur-[100px]',
                'bg-[radial-gradient(circle_at_center,#6385FF,transparent_70%)]',
                !reduced && 'animate-drift'
              )}
              style={!reduced ? { animationDelay: '-6s' } : undefined}
            />
            <div
              className="absolute inset-0 opacity-[0.04]"
              style={{
                backgroundImage:
                  'linear-gradient(to right, #2F6BFF 1px, transparent 1px), linear-gradient(to bottom, #2F6BFF 1px, transparent 1px)',
                backgroundSize: '56px 56px',
                maskImage: 'radial-gradient(ellipse 80% 60% at 50% 40%, black, transparent)',
                WebkitMaskImage: 'radial-gradient(ellipse 80% 60% at 50% 40%, black, transparent)',
              }}
            />
          </div>

          <div className="relative mx-auto grid max-w-6xl items-center gap-14 px-5 pb-24 pt-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,460px)] lg:gap-12 lg:pb-32 lg:pt-24">
            <div className={cn(!reduced && 'animate-rise')}>
              <span className="inline-flex items-center gap-2 rounded-full border border-er-700/60 bg-er-900 px-3 py-1.5 text-caption font-medium text-ink-200">
                <Lock className="h-3 w-3 text-ink-400" aria-hidden="true" />
                Private to MIT Mysore
              </span>

              <h1 className="mt-6 font-display text-[clamp(2.5rem,6vw,3.6rem)] font-bold leading-[1.03] tracking-[-0.03em] text-ink-50">
                Find the people you
                <br />
                should be building with.
              </h1>

              <p className="mt-6 max-w-[54ch] text-body-lg leading-relaxed text-ink-300">
                Campus Connect is a closed social network for your college. Find teammates for
                hackathons and projects, meet students who share your interests, and collaborate
                with people who are actually on your campus.
              </p>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Link to="/login" className={CTA}>
                  Find your team
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Link>
                <a href="#how" className={CTA_GHOST}>
                  See how it works
                </a>
              </div>

              <dl className="mt-10 flex flex-wrap gap-x-10 gap-y-4">
                {[
                  { k: 'Access', v: 'College accounts only' },
                  { k: 'Campus', v: 'MIT Mysore' },
                  { k: 'Members', v: 'Students · Alumni · Faculty' },
                ].map((s) => (
                  <div key={s.k}>
                    <dt className="text-micro font-semibold uppercase tracking-wide text-ink-400">
                      {s.k}
                    </dt>
                    <dd className="mt-0.5 text-body-sm font-medium text-ink-100">{s.v}</dd>
                  </div>
                ))}
              </dl>
            </div>

            <div className="flex justify-center lg:justify-end">
              <PeerMatchDemo />
            </div>
          </div>
        </section>

        {/* --------------------------------------------------- The premise */}
        <section className="border-t border-er-800/70 bg-er-900">
          <div className="mx-auto max-w-6xl px-5 py-16 lg:py-20">
            <Reveal>
              <p className="max-w-[62ch] font-display text-[clamp(1.25rem,2.4vw,1.6rem)] font-medium leading-[1.45] text-ink-100">
                Every campus already has the people you need. The problem is that finding them
                depends on who you happen to sit next to — and that is not a system.
              </p>
            </Reveal>
            <Reveal delay={100}>
              <p className="mt-5 max-w-[58ch] text-body text-ink-400">
                Five group chats, a notice board, and a rumour are not a network. Campus Connect
                replaces that with one closed, verified directory of your college — searchable by
                skill, interest and intent.
              </p>
            </Reveal>
          </div>
        </section>

        {/* ------------------------------------------------- How it works */}
        <section id="how" className="scroll-mt-20 border-t border-er-800/70">
          <div className="mx-auto max-w-6xl px-5 py-20 lg:py-28">
            <Reveal>
              <h2 className="max-w-[24ch] font-display text-[clamp(1.75rem,3.4vw,2.5rem)] font-bold leading-[1.1] tracking-[-0.02em] text-ink-50">
                Closed by design. Three steps to a real team.
              </h2>
            </Reveal>

            <ol className="mt-14 grid gap-10 md:grid-cols-3 md:gap-8">
              {STEPS.map((s, i) => (
                <Reveal key={s.title} delay={i * 90}>
                  <li className="relative">
                    <div className="flex items-center gap-3">
                      <span className="flex h-9 w-9 items-center justify-center rounded-xl border border-er-600/40 bg-er-800 font-display text-body-sm font-bold text-ink-100">
                        {i + 1}
                      </span>
                      <span className="h-px flex-1 bg-er-700/40" aria-hidden="true" />
                    </div>
                    <h3 className="mt-5 font-display text-heading-sm font-semibold text-ink-50">
                      {s.title}
                    </h3>
                    <p className="mt-2 text-body-sm leading-relaxed text-ink-400">{s.body}</p>
                  </li>
                </Reveal>
              ))}
            </ol>

            <Reveal delay={120}>
              <div className="mt-16 flex flex-col gap-4 rounded-2xl border border-er-600/40 bg-er-900 px-6 py-5 sm:flex-row sm:items-center">
                <Lock className="h-6 w-6 flex-shrink-0 text-ink-400" aria-hidden="true" />
                <p className="text-body-sm text-ink-200">
                  <span className="font-semibold text-ink-50">Nothing here is public.</span> There is
                  no open sign-up and no public profile on the internet. Access is tied to a college
                  account, and what you post stays inside the campus network.
                </p>
              </div>
            </Reveal>
          </div>
        </section>

        {/* ---------------------------------------------------- Capabilities */}
        <section id="what" className="scroll-mt-20 border-t border-er-800/70 bg-er-900">
          <div className="mx-auto max-w-6xl px-5 py-20 lg:py-28">
            <Reveal>
              <h2 className="max-w-[22ch] font-display text-[clamp(1.75rem,3.4vw,2.5rem)] font-bold leading-[1.1] tracking-[-0.02em] text-ink-50">
                What you actually do here.
              </h2>
              <p className="mt-4 max-w-[56ch] text-body text-ink-400">
                Four things, done properly — instead of twenty features done badly.
              </p>
            </Reveal>

            <div className="mt-14 grid gap-x-10 gap-y-12 sm:grid-cols-2">
              {CAPABILITIES.map((p, i) => (
                <Reveal key={p.title} delay={i * 80}>
                  <article className="flex gap-4">
                    <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-2xl border border-er-600/40 bg-er-800">
                      <p.icon className="h-5 w-5 text-ink-300" aria-hidden="true" />
                    </span>
                    <div className="min-w-0">
                      <h3 className="font-display text-heading-sm font-semibold text-ink-50">
                        {p.title}
                      </h3>
                      <p className="mt-2 text-body-sm leading-relaxed text-ink-400">{p.body}</p>
                    </div>
                  </article>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        {/* --------------------------------------------------------- Who */}
        <section id="who" className="scroll-mt-20 border-t border-er-800/70">
          <div className="mx-auto max-w-6xl px-5 py-20 lg:py-28">
            <Reveal>
              <h2 className="max-w-[22ch] font-display text-[clamp(1.75rem,3.4vw,2.5rem)] font-bold leading-[1.1] tracking-[-0.02em] text-ink-50">
                Everyone on campus, one network.
              </h2>
              <p className="mt-4 max-w-[56ch] text-body text-ink-400">
                Students, alumni and faculty each get a different job to do — from the same verified
                membership list.
              </p>
            </Reveal>

            <div className="mt-14 grid gap-8 md:grid-cols-3">
              {AUDIENCES.map((a, i) => (
                <Reveal key={a.role} delay={i * 90}>
                  <div className="h-full rounded-2xl border border-er-800 bg-er-900 p-6">
                    <span className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-er-600/40 bg-er-800">
                      <a.icon className="h-5 w-5 text-ink-300" aria-hidden="true" />
                    </span>
                    <h3 className="mt-4 font-display text-heading-sm font-semibold text-ink-50">
                      {a.role}
                    </h3>
                    <p className="mt-1.5 text-caption italic text-ink-400">{a.line}</p>
                    <ul className="mt-4 space-y-2.5">
                      {a.points.map((pt) => (
                        <li key={pt} className="flex items-start gap-2.5 text-body-sm text-ink-300">
                          <span
                            className="mt-[7px] h-1.5 w-1.5 flex-shrink-0 rounded-full bg-er-500"
                            aria-hidden="true"
                          />
                          {pt}
                        </li>
                      ))}
                    </ul>
                  </div>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        {/* -------------------------------------------- The assistant band */}
        {/* Deliberately one band, below the fold. The product is the network;
            the assistant is a thing the network happens to include. */}
        <section className="border-t border-er-800/70 bg-er-900">
          <div className="mx-auto max-w-6xl px-5 py-16 lg:py-20">
            <Reveal>
              <div className="flex flex-col gap-6 rounded-2xl border border-er-800 bg-er-950 px-6 py-6 sm:flex-row sm:items-center sm:gap-8">
                <div className="min-w-0 flex-1">
                  <p className="font-mono text-micro uppercase tracking-wide text-ink-400">
                    Also included
                  </p>
                  <h3 className="mt-2 font-display text-heading-md font-semibold text-ink-50">
                    Campus AI, grounded in your college documents
                  </h3>
                  <p className="mt-2 max-w-[58ch] text-body-sm leading-relaxed text-ink-400">
                    Ask about handbooks, deadlines and rules. Answers cite the document they came
                    from, and when the library does not cover something it says so instead of
                    guessing. Useful — but it is not why you are here.
                  </p>
                </div>
                <Link to="/login" className={cn(CTA_GHOST, 'flex-shrink-0')}>
                  Try the assistant
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Link>
              </div>
            </Reveal>
          </div>
        </section>

        {/* ------------------------------------------------------- Closing */}
        <section className="relative overflow-hidden border-t border-er-800/70">
          <div
            className="pointer-events-none absolute inset-0 opacity-[0.10]"
            aria-hidden="true"
          >
            <div className="absolute -top-24 right-[8%] h-80 w-80 rounded-full bg-er-500 blur-[90px]" />
            <div className="absolute -bottom-32 left-[6%] h-72 w-72 rounded-full bg-er-400 blur-[90px]" />
          </div>

          <div className="relative mx-auto max-w-3xl px-5 py-24 text-center lg:py-28">
            <p className="font-display text-[clamp(1.6rem,3.2vw,2.25rem)] font-semibold leading-[1.2] tracking-[-0.02em] text-ink-50">
              The team you need is already on your campus.
            </p>
            <p className="mx-auto mt-4 max-w-[46ch] text-body text-ink-400">
              Sign in with a college account and find them. No public profile, no open sign-up, no
              strangers.
            </p>

            <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
              <Link to="/login" className={CTA}>
                Join your campus
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
              <Link to="/login" className={CTA_GHOST}>
                Explore a demo profile
              </Link>
            </div>
          </div>
        </section>
      </main>

      {/* ---------------------------------------------------------- Footer */}
      <footer className="border-t border-er-800/70 bg-er-950">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-5 py-10 sm:flex-row sm:items-center">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-er-500">
              <Sparkles className="h-4 w-4 text-er-950" aria-hidden="true" />
            </span>
            <p className="text-body-sm font-semibold text-ink-100">Campus Connect</p>
          </div>
          <p className="text-caption text-ink-400 sm:ml-auto">
            A closed network for MIT Mysore. Membership requires a college account.
          </p>
        </div>
      </footer>
    </div>
  )
}
