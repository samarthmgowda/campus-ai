import { type HTMLAttributes, forwardRef } from 'react'
import { cn } from '../../utils/cn'

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'outline' | 'accent'
  size?: 'sm' | 'md'
}

/**
 * Badge
 * ─────
 * A status pill. Filled variants are a translucent wash of their own hue plus
 * the hue as text — the dark-canvas equivalent of the old pastel fill.
 *
 * Status hues (green / amber / red / sky) are *semantic* and sit outside the
 * 7:4:1 brand ratio on purpose: a user has to be able to tell "approved" from
 * "rejected" at a glance, and those two cannot both be blue.
 */
export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(
  ({ className, variant = 'default', size = 'md', children, ...props }, ref) => {
    const variants = {
      default: 'bg-white/[0.07] text-ink-200 border border-white/10',
      success: 'bg-emerald-500/12 text-emerald-300 border border-emerald-500/25',
      warning: 'bg-amber-500/12 text-amber-300 border border-amber-500/25',
      danger: 'bg-red-500/12 text-red-300 border border-red-500/25',
      info: 'bg-sky-500/12 text-sky-300 border border-sky-500/25',
      outline: 'bg-transparent border border-white/15 text-ink-300',
      accent: 'bg-er-500/15 text-er-200 border border-er-500/30',
    }

    const sizes = {
      sm: 'px-2 py-0.5 text-micro',
      md: 'px-2.5 py-1 text-caption',
    }

    return (
      <span
        ref={ref}
        className={cn(
          'inline-flex items-center font-semibold rounded-full whitespace-nowrap',
          variants[variant],
          sizes[size],
          className
        )}
        {...props}
      >
        {children}
      </span>
    )
  }
)

Badge.displayName = 'Badge'
