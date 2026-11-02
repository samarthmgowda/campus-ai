import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

/**
 * PageContainer
 * ─────────────
 * One decision — how wide is this page — made once, at the top of the file.
 *
 * Before this, every page hand-rolled `<div className="mx-auto max-w-5xl">`
 * and picked a width by feel, so `/community` was 2xl while `/analytics` was
 * 5xl with nothing indicating why. Widths are now named for their *content
 * shape* rather than their pixel count:
 *
 *   reading  — one column of prose. Forms, profiles, long-text views.
 *   form     — a single focused input flow (settings, search).
 *   standard — the default. Card grids, two-column splits. Most pages.
 *   wide     — data-dense dashboards that earn the extra horizontal room.
 *   full     — chat and other surfaces that manage their own scroll + width.
 */
export type PageWidth = 'reading' | 'form' | 'standard' | 'wide' | 'full'

const WIDTHS: Record<PageWidth, string> = {
  reading: 'max-w-3xl',
  form: 'max-w-2xl',
  standard: 'max-w-5xl',
  wide: 'max-w-7xl',
  full: 'max-w-none',
}

export function PageContainer({
  width = 'standard',
  className,
  children,
}: {
  width?: PageWidth
  className?: string
  children: ReactNode
}) {
  return (
    <div className={cn('mx-auto w-full', WIDTHS[width], className)}>
      {children}
    </div>
  )
}

/**
 * SectionHeader
 * ─────────────
 * The heading row that opens a section: a title, an optional description, and
 * an optional action slot opposite ("See all", a secondary button).
 *
 * The title renders as a real `<h2>` so every page has a navigable heading
 * outline — screen-reader users can jump section to section instead of
 * listening to the whole page. `level` lets a nested section drop to `<h3>`
 * without breaking the outline contract.
 */
export function SectionHeader({
  title,
  description,
  action,
  level = 2,
  className,
}: {
  title: string
  description?: string
  action?: ReactNode
  level?: 2 | 3
  className?: string
}) {
  const Tag = level === 2 ? 'h2' : 'h3'
  const size = level === 2 ? 'text-[16px]' : 'text-body-sm'
  return (
    <div className={cn('flex items-end justify-between gap-4 mb-3.5', className)}>
      <div className="min-w-0">
        <Tag className={cn('font-display font-semibold tracking-tight text-campus-950 flex items-center gap-2', size)}>
          {title}
        </Tag>
        {description && <p className="text-body-sm text-surface-500 mt-0.5">{description}</p>}
      </div>
      {action && <div className="flex-shrink-0">{action}</div>}
    </div>
  )
}

/**
 * PageSection
 * ───────────
 * A labelled band inside a page, for grouping cards under a heading.
 * Wraps SectionHeader with the surrounding `<section>` and its spacing.
 */
export function PageSection({
  title,
  description,
  action,
  level = 2,
  className,
  children,
}: {
  title?: string
  description?: string
  action?: ReactNode
  level?: 2 | 3
  className?: string
  children: ReactNode
}) {
  return (
    <section className={cn('mb-6 last:mb-0', className)}>
      {title && (
        <SectionHeader title={title} description={description} action={action} level={level} />
      )}
      {children}
    </section>
  )
}
