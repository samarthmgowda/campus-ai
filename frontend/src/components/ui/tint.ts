/**
 * Tints
 * ─────
 * The one place a "coloured chip" gets its colour.
 *
 * Icon squares, stat tiles, avatars and notification rows all need the same
 * thing: a soft background, a saturated icon colour, and a hairline border that
 * keeps the chip legible on white. Before this file each page picked its own
 * greys and blues, so the same kind of chip was three different colours
 * depending on which screen you were on.
 *
 * Every value here is a complete class triple (bg / text / border) rather than
 * three separate lookups, because they are only ever used together — and it is
 * the *border* that most often went missing when a page improvised.
 */
export type Tint =
  | 'brand'
  | 'accent'
  | 'sky'
  | 'emerald'
  | 'amber'
  | 'violet'
  | 'rose'
  | 'neutral'

export const TINTS: Record<Tint, string> = {
  brand: 'bg-campus-50 text-campus-600 border-campus-100',
  accent: 'bg-accent-50 text-accent-600 border-accent-100',
  sky: 'bg-sky-50 text-sky-600 border-sky-100',
  emerald: 'bg-emerald-50 text-emerald-600 border-emerald-100',
  amber: 'bg-amber-50 text-amber-600 border-amber-100',
  violet: 'bg-violet-50 text-violet-600 border-violet-100',
  rose: 'bg-rose-50 text-rose-600 border-rose-100',
  /** For rows that should stay quiet — "everything else" in a category map. */
  neutral: 'bg-surface-100 text-surface-500 border-surface-200',
}

/** The brand gradient, used for solid chips, avatars and banners. */
export const BRAND_GRADIENT = 'bg-gradient-to-br from-[#667eea] to-[#764ba2]'

/** The three-stop gradient, for the largest surfaces where it reads as light. */
export const BRAND_GRADIENT_WIDE = 'bg-gradient-to-br from-[#667eea] via-[#764ba2] to-[#f093fb]'
