import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'

/** Colour ramp for a stat's icon chip. Keep to these; they map to the palette. */
export type StatTint = 'brand' | 'accent' | 'sky' | 'emerald' | 'amber' | 'violet' | 'rose'

const TINTS: Record<StatTint, string> = {
  brand: 'bg-campus-50 text-campus-600 border-campus-100',
  accent: 'bg-accent-50 text-accent-600 border-accent-100',
  sky: 'bg-sky-50 text-sky-600 border-sky-100',
  emerald: 'bg-emerald-50 text-emerald-600 border-emerald-100',
  amber: 'bg-amber-50 text-amber-600 border-amber-100',
  violet: 'bg-violet-50 text-violet-600 border-violet-100',
  rose: 'bg-rose-50 text-rose-600 border-rose-100',
}

/**
 * StatTile
 * ────────
 * A single figure with a label — the atom every dashboard is built from.
 *
 * Three presentation shapes so a page doesn't have to reinvent them:
 *   'card'  — standalone tile sitting on the page canvas (default).
 *   'plain' — no chrome, for use inside a coloured banner.
 *   'inline'— compact horizontal row, for tight sidebars or list headers.
 *
 * `trend` is a pre-formatted string ("+12% this week") rather than a number,
 * because the direction of "good" differs per metric and the caller knows it.
 */
export function StatTile({
  icon: Icon,
  label,
  value,
  hint,
  tint = 'brand',
  variant = 'card',
  trend,
  trendDirection = 'neutral',
  className,
  onClick,
}: {
  icon?: LucideIcon
  label: string
  value: ReactNode
  hint?: string
  tint?: StatTint
  variant?: 'card' | 'plain' | 'inline'
  trend?: string
  trendDirection?: 'up' | 'down' | 'neutral'
  className?: string
  onClick?: () => void
}) {
  const Wrapper = onClick ? 'button' : 'div'
  const interactive = onClick
    ? 'text-left cursor-pointer transition-all duration-200 hover:shadow-card-hover hover:-translate-y-[2px] focus:outline-none focus-visible:ring-2 focus-visible:ring-campus-500'
    : ''

  if (variant === 'plain') {
    return (
      <div className={cn('min-w-0', className)}>
        {Icon && (
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 mb-2.5" aria-hidden="true">
            <Icon className="h-[18px] w-[18px] text-accent-300" />
          </span>
        )}
        <p className="font-display font-bold text-[28px] leading-none tabular-nums">{value}</p>
        <p className="text-caption text-white/60 mt-1.5">{label}</p>
      </div>
    )
  }

  if (variant === 'inline') {
    return (
      <Wrapper
        onClick={onClick}
        className={cn('flex items-center gap-3 min-w-0', className)}
      >
        {Icon && (
          <span className={cn('flex h-10 w-10 items-center justify-center rounded-xl border flex-shrink-0', TINTS[tint])} aria-hidden="true">
            <Icon className="h-[18px] w-[18px]" />
          </span>
        )}
        <div className="min-w-0">
          <p className="font-display font-bold text-[20px] leading-none tabular-nums text-campus-950">{value}</p>
          <p className="text-caption text-surface-500 mt-1 truncate">{label}</p>
        </div>
      </Wrapper>
    )
  }

  return (
    <Wrapper
      onClick={onClick}
      className={cn(
        'rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-4 sm:p-5',
        interactive,
        className
      )}
    >
      <div className="flex items-start justify-between gap-3">
        {Icon && (
          <span className={cn('flex h-10 w-10 items-center justify-center rounded-xl border flex-shrink-0', TINTS[tint])} aria-hidden="true">
            <Icon className="h-5 w-5" />
          </span>
        )}
        {trend && (
          <span
            className={cn(
              'text-micro font-semibold tabular-nums rounded-full px-2 py-0.5',
              trendDirection === 'up' && 'bg-emerald-50 text-emerald-700',
              trendDirection === 'down' && 'bg-rose-50 text-rose-700',
              trendDirection === 'neutral' && 'bg-surface-100 text-surface-500'
            )}
          >
            {trend}
          </span>
        )}
      </div>
      <p className="font-display font-bold text-[28px] leading-none tabular-nums text-campus-950 mt-3.5">{value}</p>
      <p className="text-body-sm text-surface-500 mt-1.5">{label}</p>
      {hint && <p className="text-micro text-surface-400 mt-0.5">{hint}</p>}
    </Wrapper>
  )
}

/**
 * StatGrid
 * ────────
 * Lays out StatTiles on a responsive column count without the caller needing
 * to remember the breakpoint dance. Defaults to 4 across on wide screens,
 * collapsing to 2 on phones — the pattern the dashboards were each hand-rolling.
 */
export function StatGrid({
  cols = 4,
  className,
  children,
}: {
  cols?: 2 | 3 | 4
  className?: string
  children: ReactNode
}) {
  const colClass = {
    2: 'grid-cols-1 sm:grid-cols-2',
    3: 'grid-cols-1 sm:grid-cols-3',
    4: 'grid-cols-2 lg:grid-cols-4',
  }[cols]

  return (
    <div className={cn('grid gap-3 sm:gap-4', colClass, className)}>
      {children}
    </div>
  )
}
