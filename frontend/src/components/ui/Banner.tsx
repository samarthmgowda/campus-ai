import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'
import { useReducedMotion } from '../../hooks/useReducedMotion'

/**
 * Banner
 * ──────
 * The full-width violet panel that opens a page: the student dashboard's
 * identity strip, the analytics totals, the chat welcome, the profile hero.
 *
 * Eighteen pages built this by hand, and because each one pasted its own
 * blurred blobs, the decoration differed every time — two circles here, three
 * there, at different offsets and opacities. Centralising it means the page
 * that opens with a violet band looks like the same product as the next one.
 *
 * The blobs are decorative and marked `aria-hidden`; they are rendered at low
 * opacity so text contrast is unaffected. When motion is reduced the drift is
 * dropped entirely rather than merely slowed — a slowly moving background is
 * still motion to someone who asked for none.
 */
export function Banner({
  children,
  className,
  gradient = 'brand',
  size = 'md',
}: {
  children: ReactNode
  className?: string
  /** `brand` is the two-stop ramp; `wide` adds the pink stop for hero moments. */
  gradient?: 'brand' | 'wide'
  size?: 'sm' | 'md' | 'lg'
}) {
  const reduced = useReducedMotion()

  const padding = {
    sm: 'p-5',
    md: 'p-6 sm:p-7',
    lg: 'p-6 sm:p-8',
  }[size]

  return (
    <section
      className={cn(
        'relative overflow-hidden rounded-3xl text-white shadow-card',
        gradient === 'wide'
          ? 'bg-gradient-to-br from-[#667eea] via-[#764ba2] to-[#f093fb]'
          : 'bg-gradient-to-br from-[#667eea] to-[#764ba2]',
        padding,
        className
      )}
    >
      {/* Colour washes. Kept under 12% so white body text stays at 4.5:1. */}
      <div className="pointer-events-none absolute inset-0" aria-hidden="true">
        <div
          className={cn(
            'absolute -right-16 -top-24 h-64 w-64 rounded-full bg-white/10 blur-3xl',
            !reduced && 'animate-float-slow'
          )}
        />
        <div
          className={cn(
            'absolute -bottom-28 -left-12 h-52 w-52 rounded-full bg-white/[0.07] blur-3xl',
            !reduced && 'animate-float'
          )}
          style={!reduced ? { animationDelay: '-3s' } : undefined}
        />
      </div>
      <div className="relative">{children}</div>
    </section>
  )
}

/**
 * BannerStat
 * ───────────
 * A figure sitting on a Banner. It differs from `StatTile variant="plain"` only
 * in that it inherits the banner's text colour rather than assuming the accent
 * tint, so a banner can render at any gradient without the numbers going muddy.
 */
export function BannerStat({ value, label }: { value: ReactNode; label: string }) {
  return (
    <div>
      <p className="font-display font-bold text-[26px] leading-none tabular-nums">{value}</p>
      <p className="mt-1.5 text-caption text-white/60">{label}</p>
    </div>
  )
}
