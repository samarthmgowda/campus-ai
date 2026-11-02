import { Link } from 'react-router-dom'
import { cn } from '../utils/cn'
import { useInView } from '../hooks/useInView'
import { useReducedMotion } from '../hooks/useReducedMotion'
import AnswerDemo from '../components/landing/AnswerDemo'
import { PageContainer } from '../components/ui/PageContainer'
import {
  Sparkles,
  ArrowRight,
  Bot,
  Users,
  CalendarDays,
  FolderKanban,
  Briefcase,
  GraduationCap,
  FileText,
  ShieldCheck,
  Quote,
  type LucideIcon,
} from 'lucide-react'

/* ------------------------------------------------------------------ */
/* Section reveal                                                      */
/* ------------------------------------------------------------------ */

/**
 * A single, restrained reveal for the sections below the fold.
 *
 * Content stays in the DOM and is visible to assistive tech either way; when
 * motion is reduced the transition is skipped entirely rather than delayed.
 */
function Reveal({ children, className, delay = 0 }: { children: React.ReactNode; className?: string; delay?: number }) {
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

const PILLARS: { icon: LucideIcon; title: string; body: string; example: string }[] = [
  {
    icon: Bot,
    title: 'Ask anything about campus',
    body: 'Fees, deadlines, attendance rules, hostel policy — answered from the documents your college actually publishes.',
    example: '"What is the minimum attendance for exams?"',
  },
  {
    icon: FolderKanban,
    title: 'Find people to build with',
    body: 'Describe what you want to make. Get teammates ranked by the skills the project is missing, not by who posted first.',
    example: '"Find me teammates for a machine learning project"',
  },
  {
    icon: GraduationCap,
    title: 'Learn from people ahead of you',
    body: 'Reach seniors and alumni who have walked the same path — mentorship, referrals, and roadmaps to the role you want.',
    example: '"Who can mentor me on backend interviews?"',
  },
  {
    icon: CalendarDays,
    title: 'Keep up with campus life',
    body: 'Hackathons, workshops and drives in one feed, so nothing important passes while you are heads-down in a project.',
    example: '"What hackathons are coming up this month?"',
  },
]

const STEPS = [
  {
    title: 'It reads your campus',
    body: 'Policies, handbooks, event rules and notices are indexed into a searchable library — the same source the assistant answers from.',
  },
  {
    title: 'You ask in plain words',
    body: 'No keywords, no filters, no forms. Type the question the way you would ask a senior.',
  },
  {
    title: 'You get an answer with receipts',
    body: 'Every reply cites the document and page it came from. If the library does not cover it, the assistant says so instead of guessing.',
  },
]

const AUDIENCES: { icon: LucideIcon; role: string; tint: string; points: string[] }[] = [
  {
    icon: GraduationCap,
    role: 'Students',
    tint: 'bg-campus-50 text-campus-700 border-campus-100',
    points: ['Ask the handbook anything', 'Assemble project teams', 'Track events and deadlines', 'Build a profile that gets found'],
  },
  {
    icon: Users,
    role: 'Alumni',
    tint: 'bg-emerald-50 text-emerald-700 border-emerald-100',
    points: ['Mentor students in your field', 'Post referrals where you work', 'Share the roadmap you wish you had', 'Stay in touch with your batch'],
  },
  {
    icon: Briefcase,
    role: 'Recruiters',
    tint: 'bg-sky-50 text-sky-700 border-sky-100',
    points: ['Post roles to a warm audience', 'Screen by verified skills', 'Track applications in one place', 'Reach students before graduation'],
  },
]

/* ------------------------------------------------------------------ */
/* Page                                                                */
/* ------------------------------------------------------------------ */

export default function LandingPage() {
  const reduced = useReducedMotion()

  return (
    <div className="min-h-dvh bg-[#f5f7fa] text-campus-950">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-xl focus:bg-white focus:px-4 focus:py-2.5 focus:text-body-sm focus:font-semibold focus:shadow-elevated"
      >
        Skip to content
      </a>

      {/* ---------------------------------------------------------- Header */}
      <header className="sticky top-0 z-40 border-b border-campus-900/[0.06] bg-[#f5f7fa]/80 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-5">
          <Link to="/" className="flex items-center gap-2.5 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-400">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2]">
              <Sparkles className="h-[18px] w-[18px] text-white" aria-hidden="true" />
            </span>
            <span className="font-display font-bold text-[17px] tracking-tight">Campus AI</span>
          </Link>

          <nav className="ml-6 hidden items-center gap-1 md:flex" aria-label="Page sections">
            {[
              { href: '#how', label: 'How it works' },
              { href: '#who', label: 'Who it is for' },
            ].map((l) => (
              <a
                key={l.href}
                href={l.href}
                className="rounded-lg px-3 py-2 text-body-sm font-medium text-surface-600 transition-colors hover:bg-campus-900/[0.04] hover:text-campus-950"
              >
                {l.label}
              </a>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <Link
              to="/login"
              className="rounded-xl px-3.5 py-2 text-body-sm font-semibold text-surface-600 transition-colors hover:bg-campus-900/[0.04] hover:text-campus-950"
            >
              Sign in
            </Link>
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] px-4 py-2 text-body-sm font-semibold text-white shadow-card transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500 focus-visible:ring-offset-2"
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
          {/* Background field: two soft colour washes that drift very slowly.
              Purely decorative, and frozen when motion is reduced. */}
          <div className="pointer-events-none absolute inset-0" aria-hidden="true">
            <div
              className={cn(
                'absolute -top-40 right-[-10%] h-[520px] w-[520px] rounded-full opacity-[0.22] blur-[90px]',
                'bg-[radial-gradient(circle_at_center,#667eea,transparent_70%)]',
                !reduced && 'animate-drift'
              )}
            />
            <div
              className={cn(
                'absolute -bottom-56 left-[-12%] h-[460px] w-[460px] rounded-full opacity-[0.18] blur-[90px]',
                'bg-[radial-gradient(circle_at_center,#764ba2,transparent_70%)]',
                !reduced && 'animate-drift'
              )}
              style={!reduced ? { animationDelay: '-6s' } : undefined}
            />
            {/* A faint grid, to read as an index/ledger surface rather than a void. */}
            <div
              className="absolute inset-0 opacity-[0.035]"
              style={{
                backgroundImage:
                  'linear-gradient(to right, #2e2a5c 1px, transparent 1px), linear-gradient(to bottom, #2e2a5c 1px, transparent 1px)',
                backgroundSize: '56px 56px',
                maskImage: 'radial-gradient(ellipse 80% 60% at 50% 40%, black, transparent)',
                WebkitMaskImage: 'radial-gradient(ellipse 80% 60% at 50% 40%, black, transparent)',
              }}
            />
          </div>

          <div className="relative mx-auto grid max-w-6xl items-center gap-14 px-5 pb-24 pt-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,560px)] lg:gap-10 lg:pb-32 lg:pt-24">
            {/* Left column: the claim. */}
            <div className={cn(!reduced && 'animate-rise')}>
              <span className="inline-flex items-center gap-2 rounded-full border border-campus-200 bg-white/70 px-3 py-1.5 text-caption font-medium text-campus-700">
                <span className="relative flex h-1.5 w-1.5">
                  <span className={cn('absolute inline-flex h-full w-full rounded-full bg-emerald-400', !reduced && 'animate-ping')} />
                  <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald-500" />
                </span>
                Live at MIT Mysore
              </span>

              <h1 className="mt-6 font-display font-bold tracking-[-0.03em] text-[clamp(2.5rem,6vw,3.75rem)] leading-[1.02] text-campus-950">
                Every campus question,
                <br />
                <span className="bg-gradient-to-r from-[#667eea] to-[#764ba2] bg-clip-text text-transparent">
                  answered with a source.
                </span>
              </h1>

              <p className="mt-6 max-w-[54ch] text-body-lg leading-relaxed text-surface-600">
                Campus AI reads your college's own documents and answers from them — and shows you the page
                it came from. When the answer is not in the library, it tells you that too.
              </p>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Link
                  to="/login"
                  className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] px-6 py-3 text-body font-semibold text-white shadow-card transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500 focus-visible:ring-offset-2"
                >
                  Try it as a student
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Link>
                <a
                  href="#how"
                  className="inline-flex items-center gap-2 rounded-xl border border-campus-900/15 bg-white px-6 py-3 text-body font-semibold text-campus-900 transition-colors hover:border-campus-900/30 hover:bg-campus-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500"
                >
                  See how it works
                </a>
              </div>

              <dl className="mt-10 flex flex-wrap gap-x-10 gap-y-4">
                {[
                  { k: 'Grounded', v: 'Every answer cites a source' },
                  { k: 'Roles', v: 'Student · Alumni · Recruiter' },
                  { k: 'Campus', v: 'MIT Mysore' },
                ].map((s) => (
                  <div key={s.k}>
                    <dt className="text-micro font-semibold uppercase tracking-wide text-surface-400">{s.k}</dt>
                    <dd className="mt-0.5 text-body-sm font-medium text-campus-900">{s.v}</dd>
                  </div>
                ))}
              </dl>
            </div>

            {/* Right column: the product, working. */}
            <div className="flex justify-center lg:justify-end">
              <AnswerDemo />
            </div>
          </div>
        </section>

        {/* ------------------------------------------------- How it works */}
        <section id="how" className="scroll-mt-20 border-t border-campus-900/[0.06] bg-white">
          <div className="mx-auto max-w-6xl px-5 py-20 lg:py-28">
            <Reveal>
              <h2 className="max-w-[18ch] font-display font-bold tracking-[-0.02em] text-[clamp(1.75rem,3.4vw,2.5rem)] leading-[1.1] text-campus-950">
                Three steps, and no guessing where the answer came from.
              </h2>
            </Reveal>

            {/* A genuine sequence, so numbering it is meaningful rather than decorative. */}
            <ol className="mt-14 grid gap-10 md:grid-cols-3 md:gap-8">
              {STEPS.map((s, i) => (
                <Reveal key={s.title} delay={i * 90}>
                  <li className="relative">
                    <div className="flex items-center gap-3">
                      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-campus-50 font-display text-body-sm font-bold text-campus-700">
                        {i + 1}
                      </span>
                      <span className="h-px flex-1 bg-campus-900/10" aria-hidden="true" />
                    </div>
                    <h3 className="mt-5 font-display text-heading-sm font-semibold text-campus-950">{s.title}</h3>
                    <p className="mt-2 text-body-sm leading-relaxed text-surface-600">{s.body}</p>
                  </li>
                </Reveal>
              ))}
            </ol>

            {/* The grounding promise, in the product's own voice. */}
            <Reveal delay={120}>
              <div className="mt-16 flex flex-col gap-4 rounded-2xl border border-emerald-200/70 bg-emerald-50/50 px-6 py-5 sm:flex-row sm:items-center">
                <ShieldCheck className="h-6 w-6 flex-shrink-0 text-emerald-600" aria-hidden="true" />
                <p className="text-body-sm text-emerald-900">
                  <span className="font-semibold">It will not make things up.</span> If a question falls outside the
                  campus library, the assistant says the documents do not cover it instead of inventing an answer.
                </p>
              </div>
            </Reveal>
          </div>
        </section>

        {/* ---------------------------------------------------- Capabilities */}
        <section className="border-t border-campus-900/[0.06]">
          <div className="mx-auto max-w-6xl px-5 py-20 lg:py-28">
            <Reveal>
              <h2 className="max-w-[20ch] font-display font-bold tracking-[-0.02em] text-[clamp(1.75rem,3.4vw,2.5rem)] leading-[1.1] text-campus-950">
                One place for the things campus makes hard.
              </h2>
            </Reveal>

            {/* Not identical cards: each carries a real example question, which
                is what a student would actually type. */}
            <div className="mt-14 grid gap-x-10 gap-y-12 sm:grid-cols-2">
              {PILLARS.map((p, i) => (
                <Reveal key={p.title} delay={i * 80}>
                  <article className="flex gap-4">
                    <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-2xl border border-campus-100 bg-campus-50">
                      <p.icon className="h-5 w-5 text-campus-600" aria-hidden="true" />
                    </span>
                    <div className="min-w-0">
                      <h3 className="font-display text-heading-sm font-semibold text-campus-950">{p.title}</h3>
                      <p className="mt-2 text-body-sm leading-relaxed text-surface-600">{p.body}</p>
                      <p className="mt-3 border-l-2 border-campus-200 pl-3 font-mono text-micro italic text-surface-500">
                        {p.example}
                      </p>
                    </div>
                  </article>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        {/* --------------------------------------------------------- Who */}
        <section id="who" className="scroll-mt-20 border-t border-campus-900/[0.06] bg-white">
          <div className="mx-auto max-w-6xl px-5 py-20 lg:py-28">
            <Reveal>
              <h2 className="max-w-[20ch] font-display font-bold tracking-[-0.02em] text-[clamp(1.75rem,3.4vw,2.5rem)] leading-[1.1] text-campus-950">
                Built for everyone on campus.
              </h2>
              <p className="mt-4 max-w-[56ch] text-body text-surface-600">
                Three roles, three very different jobs to do — one system that knows which one you are.
              </p>
            </Reveal>

            <div className="mt-14 grid gap-8 md:grid-cols-3">
              {AUDIENCES.map((a, i) => (
                <Reveal key={a.role} delay={i * 90}>
                  <div className="h-full rounded-2xl border border-campus-900/[0.08] bg-[#f5f7fa] p-6">
                    <span className={cn('inline-flex h-11 w-11 items-center justify-center rounded-2xl border', a.tint)}>
                      <a.icon className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <h3 className="mt-4 font-display text-heading-sm font-semibold text-campus-950">{a.role}</h3>
                    <ul className="mt-4 space-y-2.5">
                      {a.points.map((pt) => (
                        <li key={pt} className="flex items-start gap-2.5 text-body-sm text-surface-600">
                          <span className="mt-[7px] h-1.5 w-1.5 flex-shrink-0 rounded-full bg-campus-400" aria-hidden="true" />
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

        {/* ------------------------------------------------------- Closing */}
        <section className="relative overflow-hidden border-t border-campus-900/[0.06] bg-gradient-to-br from-[#667eea] via-[#764ba2] to-[#8b5fbf] text-white">
          <div className="pointer-events-none absolute inset-0 opacity-[0.14]" aria-hidden="true">
            <div className="absolute -top-24 right-[8%] h-80 w-80 rounded-full bg-white blur-[80px]" />
            <div className="absolute -bottom-32 left-[6%] h-72 w-72 rounded-full bg-white blur-[80px]" />
          </div>

          <PageContainer width="reading" className="px-5 py-24 text-center lg:py-28">
            <Quote className="mx-auto h-7 w-7 text-white/30" aria-hidden="true" />
            <p className="mt-6 font-display font-semibold tracking-[-0.01em] text-[clamp(1.5rem,3vw,2rem)] leading-[1.25]">
              "I stopped scrolling through PDFs looking for one line about attendance."
            </p>
            <p className="mt-4 text-body-sm text-white/70">A third-year student, on the first thing they asked it</p>

            <div className="mt-10 flex flex-wrap items-center justify-center gap-3">
              <Link
                to="/login"
                className="inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 text-body font-semibold text-campus-700 shadow-elevated transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-[#764ba2]"
              >
                Enter campus
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
              <Link
                to="/login"
                className="rounded-xl border border-white/25 px-6 py-3 text-body font-semibold text-white transition-colors hover:bg-white/10"
              >
                Pick a demo profile
              </Link>
            </div>
          </PageContainer>
        </section>
      </main>

      {/* ---------------------------------------------------------- Footer */}
      <footer className="border-t border-campus-900/[0.06] bg-[#f5f7fa]">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-5 py-10 sm:flex-row sm:items-center">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-[#667eea] to-[#764ba2]">
              <FileText className="h-4 w-4 text-white" aria-hidden="true" />
            </span>
            <p className="text-body-sm font-semibold text-campus-900">Campus AI</p>
          </div>
          <p className="text-caption text-surface-500 sm:ml-auto">
            A demonstration project for MIT Mysore. Answers are grounded in curated campus documents.
          </p>
        </div>
      </footer>
    </div>
  )
}
