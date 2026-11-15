import { type HTMLAttributes, forwardRef } from 'react'
import { cn } from '../../utils/cn'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'elevated' | 'outlined' | 'ink'
  padding?: 'none' | 'sm' | 'md' | 'lg'
  interactive?: boolean
}

export const Card = forwardRef<HTMLDivElement, CardProps>(
  ({ className, variant = 'default', padding = 'md', interactive, children, ...props }, ref) => {
    // `er-50` is #EEF3FF — a near-white blue, which is what made every card a
    // white block. Surfaces live at the bottom of the ramp, not the top.
    const variants = {
      default: 'bg-er-800 shadow-card border border-white/[0.07]',
      elevated: 'bg-er-800 shadow-elevated border border-white/[0.09]',
      outlined: 'bg-transparent border border-white/12',
      ink: 'bg-gradient-to-br from-er-500 to-er-700 text-white border border-white/20',
    }

    const paddings = {
      none: '',
      sm: 'p-4',
      md: 'p-5',
      lg: 'p-6',
    }

    return (
      <div
        ref={ref}
        className={cn(
          'rounded-2xl',
          variants[variant],
          paddings[padding],
          interactive && 'transition-all duration-200 hover:shadow-card-hover hover:-translate-y-[2px] hover:border-er-500/30',
          className
        )}
        {...props}
      >
        {children}
      </div>
    )
  }
)

Card.displayName = 'Card'

export const CardHeader = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, children, ...props }, ref) => (
    <div ref={ref} className={cn('mb-4', className)} {...props}>
      {children}
    </div>
  )
)

CardHeader.displayName = 'CardHeader'

export const CardTitle = forwardRef<HTMLHeadingElement, HTMLAttributes<HTMLHeadingElement>>(
  ({ className, children, ...props }, ref) => (
    <h3 ref={ref} className={cn('font-display font-semibold text-[17px] tracking-tight text-ink-50', className)} {...props}>
      {children}
    </h3>
  )
)

CardTitle.displayName = 'CardTitle'

export const CardDescription = forwardRef<HTMLParagraphElement, HTMLAttributes<HTMLParagraphElement>>(
  ({ className, children, ...props }, ref) => (
    <p ref={ref} className={cn('mt-1 text-body-sm text-ink-400', className)} {...props}>
      {children}
    </p>
  )
)

CardDescription.displayName = 'CardDescription'

export const CardContent = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, children, ...props }, ref) => (
    <div ref={ref} className={cn(className)} {...props}>
      {children}
    </div>
  )
)

CardContent.displayName = 'CardContent'

export const CardFooter = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, children, ...props }, ref) => (
    <div ref={ref} className={cn('mt-4 flex items-center gap-2', className)} {...props}>
      {children}
    </div>
  )
)

CardFooter.displayName = 'CardFooter'
