import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { BRAND_GRADIENT, TINTS, type Tint } from './tint'

/** The four sizes the app actually uses; anything else was drift. */
const SIZES = {
  sm: { box: 'h-8 w-8 rounded-lg', icon: 'h-4 w-4' },
  md: { box: 'h-9 w-9 rounded-lg', icon: 'h-4 w-4' },
  lg: { box: 'h-10 w-10 rounded-xl', icon: 'h-5 w-5' },
  xl: { box: 'h-11 w-11 rounded-2xl', icon: 'h-5 w-5' },
} as const

/**
 * IconChip
 * ────────
 * A Lucide icon in a soft rounded square — the single most repeated element in
 * the app. It appeared in fourteen different geometries across the pages (every
 * combination of 8/9/10/11/14 px box, lg/xl/2xl radius, tinted/white/gradient
 * fill), which is why no two screens' list rows lined up.
 *
 * `fill="brand"` switches to the solid violet gradient for the chips that
 * anchor a page (the AI sparkle, an upload affordance) — those are visually
 * "louder" on purpose and shouldn't be tinted like an ordinary list row.
 * `fill="ghost"` is for chips sitting *on* a violet banner, where a tinted
 * background would disappear and the icon needs to be the accent colour.
 */
export function IconChip({
  icon: Icon,
  tint = 'brand',
  size = 'lg',
  fill = 'tint',
  className,
}: {
  icon: LucideIcon
  tint?: Tint
  size?: keyof typeof SIZES
  fill?: 'tint' | 'brand' | 'ghost'
  className?: string
}) {
  const s = SIZES[size]
  const fillClass =
    fill === 'brand'
      ? cn(BRAND_GRADIENT, 'border-transparent text-white')
      : fill === 'ghost'
        ? 'border-transparent bg-white/10 text-accent-300'
        : TINTS[tint]
  return (
    <span
      className={cn('flex flex-shrink-0 items-center justify-center border', s.box, fillClass, className)}
      aria-hidden="true"
    >
      <Icon className={s.icon} />
    </span>
  )
}

/** Sizes for the initials avatar, matching the chip scale. */
const AVATAR_SIZES = {
  sm: 'h-8 w-8 text-micro',
  md: 'h-9 w-9 text-body-sm',
  lg: 'h-10 w-10 text-body-sm',
  xl: 'h-11 w-11 text-body',
  '2xl': 'h-14 w-14 text-[22px] rounded-2xl',
} as const

/**
 * Avatar
 * ──────
 * Initials in a circle, on the brand gradient.
 *
 * Deliberately not an image component: the demo has no avatar uploads, and an
 * invented photo would imply one exists. Initials are honest and always render.
 * The letter is marked `aria-hidden` because the name is always printed next to
 * it — announcing "S" before "Samarth" is noise.
 *
 * `square` gives the rounded-square treatment the profile hero uses.
 */
export function Avatar({
  name,
  size = 'lg',
  square = false,
  muted = false,
  className,
}: {
  name?: string | null
  size?: keyof typeof AVATAR_SIZES
  square?: boolean
  /** Neutral fill, for placeholders where there is no person yet. */
  muted?: boolean
  className?: string
}) {
  const letter = (name ?? '').trim().charAt(0).toUpperCase()
  return (
    <span
      className={cn(
        'flex flex-shrink-0 items-center justify-center font-display font-bold',
        square ? 'rounded-2xl' : 'rounded-full',
        AVATAR_SIZES[size],
        muted ? 'bg-surface-100 text-surface-500' : cn(BRAND_GRADIENT, 'text-white'),
        className
      )}
      aria-hidden="true"
    >
      {letter || '?'}
    </span>
  )
}

/**
 * SkillPill / TagList
 * ───────────────────
 * The small violet-bordered pills used for skills, categories and event types.
 * Wraps by default so a long list never pushes a layout wider than its column.
 */
export function SkillPill({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border border-campus-100 bg-campus-50 px-2.5 py-1 text-micro font-medium text-campus-700',
        className
      )}
    >
      {children}
    </span>
  )
}
