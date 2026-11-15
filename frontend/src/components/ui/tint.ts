/**
 * Tints
 * ─────
 * The one place a "coloured chip" gets its colour.
 *
 * Icon squares, stat tiles, avatars and notification rows all need the same
 * thing: a raised midnight background, a saturated icon colour, and a hairline
 * border that keeps the chip legible on #090A10.
 *
 * Every value is a complete class triple (bg / text / border) rather than three
 * separate lookups, because they are only ever used together — and it is the
 * *border* that most often went missing when a page improvised.
 *
 * On a dark canvas a tint is no longer "light wash + dark ink". It is a low-
 * opacity fill of the hue plus the hue itself as text:
 *
 *   /10 background  →  the tile reads as a coloured surface against midnight
 *   the 300/400 step →  the icon reads at 4.5:1 without glowing
 *   /30 border       →  the tile keeps an edge without a hard outline
 *
 * `brand` is electric blue, which is the default tint everywhere. `accent` is
 * also blue (it used to be violet) so the two are interchangeable; consumers
 * that asked for "accent" keep working without a visual break.
 *
 * There is deliberately no `violet` key. The palette is midnight, electric blue
 * and sunset orange; a purple tile was the last thing on screen that did not
 * belong to it, and keeping the option around only invited it back. A page that
 * needs a fourth colour for a *category* (not a status) reaches for one of the
 * hue ramps below instead.
 */
export type Tint =
  | 'brand'
  | 'accent'
  | 'sky'
  | 'emerald'
  | 'amber'
  | 'rose'
  | 'teal'
  | 'neutral'

export const TINTS: Record<Tint, string> = {
  brand: 'bg-er-500/12 text-er-300 border-er-500/30',
  accent: 'bg-er-500/12 text-er-300 border-er-500/30',
  sky: 'bg-sky-400/12 text-sky-300 border-sky-400/30',
  emerald: 'bg-emerald-400/12 text-emerald-300 border-emerald-400/30',
  // Amber sits next to the CTA orange, so it is held back to a border-only
  // treatment on quiet chips — see the note in `StatTile`.
  amber: 'bg-orange-500/10 text-orange-300 border-orange-500/25',
  rose: 'bg-rose-400/12 text-rose-300 border-rose-400/30',
  teal: 'bg-teal-400/12 text-teal-300 border-teal-400/30',
  /** For rows that should stay quiet — "everything else" in a category map. */
  neutral: 'bg-white/[0.04] text-ink-400 border-white/10',
}

/**
 * The brand gradient, used for solid chips, avatars and banners.
 *
 * Electric blue, two stops of the same hue. It replaced `#667eea → #764ba2`,
 * which was violet and read as a different product to the landing page.
 * A two-stop blue gradient still gives a large surface some depth without
 * introducing a second hue.
 */
export const BRAND_GRADIENT = 'bg-gradient-to-br from-er-500 to-er-700'

/** The wider gradient, for large surfaces where it reads as light. */
export const BRAND_GRADIENT_WIDE = 'bg-gradient-to-br from-er-400 via-er-500 to-er-700'
