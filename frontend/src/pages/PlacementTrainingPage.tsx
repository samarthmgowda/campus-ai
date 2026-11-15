import { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader, LoadingState, EmptyState, ErrorState } from '../components/ui/PageState'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { api } from '../api/client'
import { cn } from '../utils/cn'
import {
  Youtube, BookOpen, FileText, Globe, Map, ArrowLeft, ArrowRight,
  Briefcase, Building2, ChevronDown, GraduationCap, ExternalLink, MapPin,
} from 'lucide-react'

/**
 * Placement & Training
 * ────────────────────
 * Two collections behind one route: alumni career roadmaps, and open job
 * postings from recruiters. Both tabs read live endpoints — `/roadmaps` and
 * `/recruiter/postings` — and neither invents data.
 *
 * This page previously declared a `role / company / ctc / requirements /
 * skills` shape that `/roadmaps` has never returned, so every card rendered
 * blank text and the resource area could never appear. The interfaces below
 * mirror `RoadmapController.toMap` and `RecruiterPostingService.PostingDto`
 * exactly; if either changes, these must change with it.
 */

type ResourceKind = 'youtube' | 'blog' | 'pdf' | 'website'

interface RoadmapResource {
  type: ResourceKind
  url: string
  label?: string
}

/** Mirrors RoadmapController.toMap(). */
interface RoadmapDto {
  id: number
  title: string
  targetRole: string
  description: string
  resources: RoadmapResource[]
  alumniId?: number
  alumniName?: string
  alumniRole?: string | null
  alumniCompany?: string | null
}

/** Mirrors RecruiterPostingService.PostingDto. */
interface PostingDto {
  id: number
  recruiterId: number | null
  recruiterName: string | null
  company: string | null
  title: string
  description: string | null
  requiredSkills: string | null
  status: string
  createdAt: string | null
  applicantCount: number
}

const RESOURCE_META: Record<ResourceKind, { icon: typeof Youtube; verb: string }> = {
  youtube: { icon: Youtube, verb: 'Watch' },
  blog: { icon: BookOpen, verb: 'Read' },
  pdf: { icon: FileText, verb: 'Open PDF' },
  website: { icon: Globe, verb: 'Visit' },
}

/** Trimmed description that never leaves a dangling ellipsis or a cut word. */
function clamp(text: string | null | undefined, max: number): string {
  const value = (text ?? '').trim()
  if (value.length <= max) return value
  const cut = value.slice(0, max)
  const lastSpace = cut.lastIndexOf(' ')
  return `${(lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`
}

function relativeDate(iso: string | null): string {
  if (!iso) return ''
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const days = Math.floor((Date.now() - then) / 86_400_000)
  if (days <= 0) return 'Today'
  if (days === 1) return 'Yesterday'
  if (days < 30) return `${days}d ago`
  const months = Math.floor(days / 30)
  return months < 12 ? `${months}mo ago` : `${Math.floor(months / 12)}y ago`
}

function skillsOf(posting: PostingDto): string[] {
  return (posting.requiredSkills ?? '')
    .split(/[,;|]/)
    .map(s => s.trim())
    .filter(Boolean)
    .slice(0, 6)
}

export default function PlacementTrainingPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab: 'careers' | 'roadmaps' =
    searchParams.get('tab') === 'roadmaps' ? 'roadmaps' : 'careers'

  const selectTab = (tab: 'careers' | 'roadmaps') => {
    setSearchParams(tab === 'careers' ? {} : { tab }, { replace: true })
  }

  return (
    <PageContainer width="standard">
      <PageHeader
        title="Placement & Training"
        sub="Career roadmaps written by alumni, and roles recruiters are hiring for right now."
      />

      {/* Tabs. A real tablist so screen readers announce the relationship
          between the two buttons and the panel below them. */}
      <div
        role="tablist"
        aria-label="Placement and training sections"
        className="inline-flex items-center gap-1 mb-8 p-1 rounded-full bg-er-900 border border-white/[0.06]"
      >
        {([
          ['careers', 'Job postings'],
          ['roadmaps', 'Career roadmaps'],
        ] as const).map(([tab, label]) => (
          <button
            key={tab}
            role="tab"
            type="button"
            aria-selected={activeTab === tab}
            aria-controls={`panel-${tab}`}
            id={`tab-${tab}`}
            onClick={() => selectTab(tab)}
            className={cn(
              'px-4 py-2 rounded-full text-body-sm font-semibold transition-colors duration-150',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-400 focus-visible:ring-offset-2 focus-visible:ring-offset-er-950',
              activeTab === tab
                ? 'bg-er-500 text-white'
                : 'text-ink-300 hover:text-ink-50 hover:bg-white/[0.06]'
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {activeTab === 'roadmaps' ? (
        <div role="tabpanel" id="panel-roadmaps" aria-labelledby="tab-roadmaps">
          <RoadmapCollection />
        </div>
      ) : (
        <div role="tabpanel" id="panel-careers" aria-labelledby="tab-careers">
          <JobPostings />
        </div>
      )}
    </PageContainer>
  )
}

/* ------------------------------------------------------------------ *
 * Career roadmaps
 * ------------------------------------------------------------------ */

function RoadmapCollection() {
  const { data: roadmaps = [], isLoading, error, refetch } = useQuery({
    queryKey: ['roadmaps'],
    queryFn: () => api.get<RoadmapDto[]>('/roadmaps'),
  })

  // Which roadmap is "current". Prev/Next move it, and clicking a card sets it.
  const [activeIndex, setActiveIndex] = useState(0)
  const cardRefs = useRef<Array<HTMLElement | null>>([])

  // The list can shrink under us (a filter, a refetch); keep the index legal.
  useEffect(() => {
    setActiveIndex(i => (roadmaps.length === 0 ? 0 : Math.min(i, roadmaps.length - 1)))
  }, [roadmaps.length])

  const goTo = useCallback((next: number) => {
    setActiveIndex(next)
    cardRefs.current[next]?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [])

  if (isLoading) return <LoadingState label="Loading roadmaps…" />

  if (error) {
    return (
      <ErrorState
        title="Roadmaps unavailable"
        message={error.message}
        onRetry={() => refetch()}
      />
    )
  }

  if (roadmaps.length === 0) {
    return (
      <EmptyState
        icon={Map}
        title="No roadmaps yet"
        hint="Alumni have not published any career roadmaps yet — when they do, they will appear here."
      />
    )
  }

  const current = roadmaps[Math.min(activeIndex, roadmaps.length - 1)]

  return (
    <>
      {/* Prev / Next. Disabled at the ends, as the two ends of a sequence
          should be. The counter is a live region so the move is announced. */}
      <div className="flex items-center justify-between gap-4 mb-6 flex-wrap">
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => goTo(activeIndex - 1)}
            disabled={activeIndex === 0}
            aria-label="Previous roadmap"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Previous
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => goTo(activeIndex + 1)}
            disabled={activeIndex >= roadmaps.length - 1}
            aria-label="Next roadmap"
          >
            Next
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>

        <p className="text-caption text-ink-400 tabular-nums" aria-live="polite">
          <span className="text-ink-200 font-semibold">{activeIndex + 1}</span>
          {' of '}
          {roadmaps.length}
          {current ? <span className="text-ink-500"> · {current.targetRole}</span> : null}
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {roadmaps.map((roadmap, index) => (
          <RoadmapCard
            key={roadmap.id}
            roadmap={roadmap}
            active={index === activeIndex}
            onSelect={() => goTo(index)}
            ref={el => { cardRefs.current[index] = el }}
          />
        ))}
      </div>
    </>
  )
}

function RoadmapCard({
  roadmap,
  active,
  onSelect,
  ref,
}: {
  roadmap: RoadmapDto
  active: boolean
  onSelect: () => void
  ref: React.Ref<HTMLElement>
}) {
  const resources = roadmap.resources ?? []

  return (
    <article
      ref={ref}
      className={cn(
        'group relative flex flex-col rounded-card bg-er-900 border transition-all duration-200',
        'hover:-translate-y-0.5',
        active
          ? 'border-er-500/60 shadow-glow'
          : 'border-white/[0.06] hover:border-white/[0.14]'
      )}
    >
      {/* The whole card is the control that makes it "current" — a real button
          so it is reachable by keyboard, with the selected state exposed. */}
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={active}
        className="flex-1 text-left p-5 rounded-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-400 focus-visible:ring-offset-2 focus-visible:ring-offset-er-950"
      >
        <div className="flex items-start gap-3 mb-3">
          <span
            className={cn(
              'flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl border transition-colors',
              active
                ? 'bg-er-500/15 border-er-500/40'
                : 'bg-white/[0.04] border-white/[0.08]'
            )}
            aria-hidden="true"
          >
            <GraduationCap className={cn('h-[18px] w-[18px]', active ? 'text-er-300' : 'text-ink-400')} />
          </span>

          <div className="min-w-0 flex-1">
            <h3 className="font-display font-semibold text-body-lg leading-snug text-ink-50">
              {roadmap.title}
            </h3>
            <p className="text-body-sm text-er-300 mt-0.5 truncate">{roadmap.targetRole}</p>
          </div>
        </div>

        <p className="text-body-sm text-ink-300 leading-relaxed">
          {clamp(roadmap.description, 130)}
        </p>
      </button>

      {/* Footer: who wrote it, and how much is attached. Kept outside the
          button so these are not part of the card's accessible name. */}
      <div className="px-5 pb-4 pt-0">
        <div className="flex items-center justify-between gap-3 pt-3 border-t border-white/[0.06]">
          <p className="text-caption text-ink-400 truncate min-w-0">
            {roadmap.alumniName ? (
              <>
                <span className="text-ink-300">{roadmap.alumniName}</span>
                {roadmap.alumniCompany ? <span className="text-ink-500"> · {roadmap.alumniCompany}</span> : null}
              </>
            ) : (
              <span className="text-ink-500">Campus Connect</span>
            )}
          </p>

          <span
            className={cn(
              'flex items-center gap-1 text-caption flex-shrink-0',
              resources.length > 0 ? 'text-er-300' : 'text-ink-500'
            )}
          >
            <BookOpen className="h-3.5 w-3.5" aria-hidden="true" />
            {resources.length > 0 ? `${resources.length} resource${resources.length === 1 ? '' : 's'}` : 'No resources'}
          </span>
        </div>

        {/* Resource area. Always in the DOM so the card height is stable; it
            expands when the card is current. */}
        <div
          className={cn(
            'grid transition-all duration-300 ease-out',
            active && resources.length > 0
              ? 'grid-rows-[1fr] opacity-100 mt-3'
              : 'grid-rows-[0fr] opacity-0 mt-0'
          )}
        >
          <div className="overflow-hidden">
            <ul className="space-y-1 pt-1">
              {resources.map((resource, i) => {
                const meta = RESOURCE_META[resource.type] ?? RESOURCE_META.website
                const Icon = meta.icon
                return (
                  <li key={`${resource.url}-${i}`}>
                    <a
                      href={resource.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="group/link flex items-center gap-2 rounded-lg px-2 py-1.5 -mx-2 text-caption text-ink-300 hover:text-ink-50 hover:bg-white/[0.05] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-400"
                    >
                      <Icon className="h-3.5 w-3.5 flex-shrink-0 text-er-400" aria-hidden="true" />
                      <span className="truncate">
                        {resource.label || `${meta.verb} resource`}
                      </span>
                      <ExternalLink
                        className="h-3 w-3 flex-shrink-0 opacity-0 group-hover/link:opacity-100 transition-opacity"
                        aria-hidden="true"
                      />
                      {/* The link text alone ("Watch resource") does not say
                          where it goes; this gives it a distinct name. */}
                      <span className="sr-only">(opens in a new tab)</span>
                    </a>
                  </li>
                )
              })}
            </ul>
          </div>
        </div>
      </div>
    </article>
  )
}

/* ------------------------------------------------------------------ *
 * Job postings
 * ------------------------------------------------------------------ */

function JobPostings() {
  const { data: postings = [], isLoading, error, refetch } = useQuery({
    queryKey: ['postings', 'OPEN'],
    queryFn: () => api.get<PostingDto[]>('/recruiter/postings?status=OPEN'),
  })

  const [openId, setOpenId] = useState<number | null>(null)

  const sorted = useMemo(
    () =>
      [...postings].sort((a, b) => {
        const at = a.createdAt ? new Date(a.createdAt).getTime() : 0
        const bt = b.createdAt ? new Date(b.createdAt).getTime() : 0
        return bt - at
      }),
    [postings]
  )

  if (isLoading) return <LoadingState label="Loading job postings…" />

  if (error) {
    return (
      <ErrorState
        title="Job postings unavailable"
        message={error.message}
        onRetry={() => refetch()}
      />
    )
  }

  if (sorted.length === 0) {
    return (
      <EmptyState
        icon={Briefcase}
        title="No open roles right now"
        hint="Recruiters have not posted any open positions yet. Check back after the next hiring drive."
      />
    )
  }

  return (
    <div className="space-y-3">
      {sorted.map(posting => {
        const skills = skillsOf(posting)
        const isOpen = openId === posting.id
        const posted = relativeDate(posting.createdAt)

        return (
          <article
            key={posting.id}
            className={cn(
              'rounded-card bg-er-900 border transition-colors duration-200',
              isOpen ? 'border-er-500/40' : 'border-white/[0.06] hover:border-white/[0.14]'
            )}
          >
            <button
              type="button"
              onClick={() => setOpenId(isOpen ? null : posting.id)}
              aria-expanded={isOpen}
              aria-controls={`posting-${posting.id}`}
              className="w-full text-left p-5 rounded-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-er-400 focus-visible:ring-offset-2 focus-visible:ring-offset-er-950"
            >
              <div className="flex items-start gap-4">
                <span
                  className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-white/[0.04] border border-white/[0.08]"
                  aria-hidden="true"
                >
                  <Building2 className="h-5 w-5 text-er-300" />
                </span>

                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h3 className="font-display font-semibold text-body-lg leading-snug text-ink-50">
                        {posting.title}
                      </h3>
                      <p className="text-body-sm text-ink-300 mt-0.5 truncate">
                        {posting.company || posting.recruiterName || 'Campus Connect'}
                      </p>
                    </div>
                    <ChevronDown
                      className={cn(
                        'h-4 w-4 flex-shrink-0 text-ink-400 transition-transform duration-200 mt-1',
                        isOpen && 'rotate-180'
                      )}
                      aria-hidden="true"
                    />
                  </div>

                  <div className="flex items-center gap-2 mt-2.5 flex-wrap">
                    <Badge variant="success" size="sm">Open</Badge>
                    {posted && <span className="text-micro text-ink-500">{posted}</span>}
                    {posting.applicantCount > 0 && (
                      <span className="text-micro text-ink-500">
                        {posting.applicantCount} applicant{posting.applicantCount === 1 ? '' : 's'}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </button>

            {isOpen && (
              <div id={`posting-${posting.id}`} className="px-5 pb-5 pt-0">
                <div className="pt-4 border-t border-white/[0.06]">
                  {posting.description ? (
                    <p className="text-body-sm text-ink-300 leading-relaxed whitespace-pre-wrap">
                      {posting.description}
                    </p>
                  ) : (
                    <p className="text-body-sm text-ink-500">No description provided.</p>
                  )}

                  {skills.length > 0 && (
                    <div className="mt-4">
                      <p className="text-micro font-semibold text-ink-400 mb-2">Skills</p>
                      <div className="flex flex-wrap gap-1.5">
                        {skills.map(skill => (
                          <Badge key={skill} variant="outline" size="sm">{skill}</Badge>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="mt-4 flex items-center gap-2 text-caption text-ink-500">
                    <MapPin className="h-3.5 w-3.5" aria-hidden="true" />
                    Applications are handled by the recruiter who posted this role.
                  </div>
                </div>
              </div>
            )}
          </article>
        )
      })}
    </div>
  )
}
