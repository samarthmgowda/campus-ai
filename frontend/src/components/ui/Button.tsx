import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '../../utils/cn'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'accent' | 'secondary' | 'outline' | 'ghost' | 'destructive'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', loading, disabled, children, ...props }, ref) => {
    // `ring-offset-er-950` matters on a dark canvas: Tailwind's default offset
    // colour is white, which drew a bright halo around every focused button.
    const baseStyles = 'inline-flex items-center justify-center font-semibold rounded-[20px] transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-er-950 disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.98]'

    const variants = {
      // The one place blue is allowed at full strength: a primary action.
      primary: 'bg-gradient-to-br from-er-500 to-er-600 text-white hover:-translate-y-[2px] hover:shadow-glow focus-visible:ring-er-400 shadow-card',
      accent: 'bg-gradient-to-br from-er-500 to-er-600 text-white hover:-translate-y-[2px] hover:shadow-glow focus-visible:ring-er-400 shadow-card',
      // Quiet fills are white at low opacity, not a light-blue ramp — `er-100`
      // is #D7E2FF and made a secondary button the brightest thing on screen.
      secondary: 'bg-white/[0.08] text-ink-100 border border-white/10 hover:bg-white/[0.13] hover:border-white/20 focus-visible:ring-er-400',
      outline: 'border border-white/15 bg-transparent text-ink-200 hover:border-white/25 hover:bg-white/[0.05] hover:text-ink-50 focus-visible:ring-er-400',
      ghost: 'text-ink-300 hover:bg-white/[0.06] hover:text-ink-50 focus-visible:ring-er-400',
      destructive: 'bg-red-600 text-white hover:bg-red-500 focus-visible:ring-red-400',
    }

    const sizes = {
      sm: 'px-3 py-1.5 text-body-sm gap-1.5',
      md: 'px-4 py-2.5 text-body-sm gap-2',
      lg: 'px-6 py-3 text-body gap-2',
    }

    return (
      <button
        ref={ref}
        className={cn(baseStyles, variants[variant], sizes[size], className)}
        disabled={disabled || loading}
        {...props}
      >
        {loading && (
          <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
        )}
        {children}
      </button>
    )
  }
)

Button.displayName = 'Button'
