import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

/**
 * Panel
 * ─────
 * The raised surface that holds a titled region of a page.
 *
 * This exact combination — `rounded-2xl border border-er-800
 * bg-er-800 shadow-card` — was pasted 49 times across the pages, with the
 * border opacity drifting between 0.04 and 0.08 as each page was written. That
 * drift is invisible in isolation and obvious in a sidebar of screenshots: no
 * two panels had the same edge weight.
 *
 * On midnight the edge is doing more work than it did on white: a shadow is
 * nearly invisible against #090A10, so the hairline is what actually separates
 * the card from the canvas. That is why it is a ring of white at low opacity
 * rather than a darker border — you cannot go darker than the background.
 *
 * `divided` adds the hairline separators between children that list panels
 * need, since `divide-y` cannot be applied from outside. `inset` is for panels
 * whose header carries its own tinted background and therefore must not have
 * the panel's padding.
 */
export function Panel({
  className,
  children,
  padded = true,
  divided = false,
}: {
  className?: string
  children: ReactNode
  /** Set false when the panel manages its own padding (e.g. a full-bleed list). */
  padded?: boolean
  /** Hairline rules between direct children. */
  divided?: boolean
}) {
  return (
    <div
      className={cn(
        'rounded-2xl border border-white/[0.07] bg-er-800 shadow-card',
        padded && 'p-4 sm:p-5',
        divided && 'overflow-hidden divide-y divide-white/[0.06]',
        className
      )}
    >
      {children}
    </div>
  )
}

/**
 * PanelHeader
 * ───────────
 * The title row inside a Panel. `tinted` gives it the recessed band that
 * filterable panels use, with the rule underneath already in place.
 */
export function PanelHeader({
  title,
  description,
  icon,
  action,
  tinted = false,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  icon?: ReactNode
  action?: ReactNode
  tinted?: boolean
  className?: string
}) {
  return (
    <div
      className={cn(
        'flex flex-wrap items-center justify-between gap-3',
        tinted && 'border-b border-white/[0.06] bg-er-900/60 px-5 pb-4 pt-5',
        className
      )}
    >
      <div className="min-w-0">
        <p className="flex items-center gap-2 font-display font-semibold text-[16px] text-ink-100">
          {icon}
          {title}
        </p>
        {description && <p className="mt-0.5 text-caption text-ink-400">{description}</p>}
      </div>
      {action && <div className="flex-shrink-0">{action}</div>}
    </div>
  )
}

/**
 * ListRow
 * ───────
 * A single line item inside a Panel: chip, a title with optional meta line, and
 * an action slot on the right.
 *
 * The `inactiveOpacity` behaviour on the action slot is the pattern the Library
 * and Admin pages wanted — row actions that appear on hover — exposed here so
 * those pages don't each re-invent the group/hover plumbing.
 */
export function ListRow({
  icon,
  title,
  meta,
  badge,
  action,
  revealActionOnHover = false,
  className,
}: {
  icon?: ReactNode
  title: ReactNode
  meta?: ReactNode
  badge?: ReactNode
  action?: ReactNode
  revealActionOnHover?: boolean
  className?: string
}) {
  return (
    <div
      className={cn(
        'group flex items-center gap-3 px-3 py-2.5 transition-colors hover:bg-white/[0.03]',
        className
      )}
    >
      {icon}
      <div className="min-w-0 flex-1">
        <p className="truncate text-body-sm font-medium text-ink-100">{title}</p>
        {meta && <p className="truncate text-micro text-ink-400">{meta}</p>}
        {badge && <div className="mt-1">{badge}</div>}
      </div>
      {action && (
        <div
          className={cn(
            'flex flex-shrink-0 items-center gap-1',
            revealActionOnHover && 'opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100'
          )}
        >
          {action}
        </div>
      )}
    </div>
  )
}

/** Consistent hover treatment for an icon-only row action. */
export const ROW_ACTION =
  'rounded-lg p-2 text-ink-400 transition-colors hover:bg-white/[0.06] hover:text-er-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-er-500'

/** The destructive variant, separated so it can never be clicked by accident. */
export const ROW_ACTION_DANGER =
  'rounded-lg p-2 text-ink-500 transition-colors hover:bg-red-500/12 hover:text-red-400 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500'
