import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { TINTS, type Tint } from './tint'

/** Colour ramp for a stat's icon chip. Keep to these; they map to the palette. */
export type StatTint = Tint

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
 *
 * The figure itself is always `ink-50`, never blue: a dashboard is 20 numbers
 * on one screen, and if the numbers are the accent then the accent stops
 * meaning anything. Blue is spent on the icon chip instead — one small, dense
 * hit per tile, which is how the 7:4:1 ratio survives a page with this many
 * elements on it.
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
    ? 'text-left cursor-pointer transition-all duration-200 hover:shadow-card-hover hover:-translate-y-[2px] hover:border-er-500/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-er-500'
    : ''

  if (variant === 'plain') {
    return (
      <div className={cn('min-w-0', className)}>
        {Icon && (
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/15 mb-2.5" aria-hidden="true">
            <Icon className="h-[18px] w-[18px] text-white" />
          </span>
        )}
        <p className="font-display font-bold text-[28px] leading-none tabular-nums text-white">{value}</p>
        <p className="text-caption text-white/70 mt-1.5">{label}</p>
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
          <p className="font-display font-bold text-[20px] leading-none tabular-nums text-ink-50">{value}</p>
          <p className="text-caption text-ink-400 mt-1 truncate">{label}</p>
        </div>
      </Wrapper>
    )
  }

  return (
    <Wrapper
      onClick={onClick}
      className={cn(
        'rounded-2xl border border-white/[0.07] bg-er-800 shadow-card p-4 sm:p-5',
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
              trendDirection === 'up' && 'bg-emerald-500/12 text-emerald-300',
              trendDirection === 'down' && 'bg-rose-500/12 text-rose-300',
              trendDirection === 'neutral' && 'bg-white/[0.06] text-ink-400'
            )}
          >
            {trend}
          </span>
        )}
      </div>
      <p className="font-display font-bold text-[28px] leading-none tabular-nums text-ink-50 mt-3.5">{value}</p>
      <p className="text-body-sm text-ink-400 mt-1.5">{label}</p>
      {hint && <p className="text-micro text-ink-500 mt-0.5">{hint}</p>}
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
