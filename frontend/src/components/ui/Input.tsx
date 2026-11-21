import { forwardRef, type InputHTMLAttributes } from 'react'
import { cn } from '../../utils/cn'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  helperText?: string
  rows?: number
}

/**
 * Input
 * ─────
 * The field is a *recessed* plane — `er-900`, one step darker than the `er-800`
 * card it usually sits on. On a light canvas an input reads as recessed because
 * it is white against grey; on midnight that logic inverts, and a field that is
 * lighter than its panel looks like a raised button instead.
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, helperText, id, ...props }, ref) => {
    const inputId = id || label?.toLowerCase().replace(/\s+/g, '-')

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-body-sm font-medium text-ink-200 mb-1.5">
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={cn(
            'w-full rounded-lg border bg-er-900 px-3 py-2 text-body text-ink-100',
            'placeholder:text-ink-500',
            'focus:outline-none focus:ring-2 focus:ring-er-500 focus:border-transparent',
            'disabled:bg-white/[0.03] disabled:text-ink-500 disabled:cursor-not-allowed',
            'transition-colors duration-150',
            error
              ? 'border-red-500/50 text-red-100 placeholder-red-400/50 focus:ring-red-500'
              : 'border-white/12 hover:border-white/20',
            className
          )}
          aria-invalid={error ? 'true' : 'false'}
          aria-describedby={error ? `${inputId}-error` : helperText ? `${inputId}-helper` : undefined}
          {...props}
        />
        {error && (
          <p id={`${inputId}-error`} className="mt-1.5 text-body-sm text-red-400" role="alert">
            {error}
          </p>
        )}
        {helperText && !error && (
          <p id={`${inputId}-helper`} className="mt-1.5 text-body-sm text-ink-400">
            {helperText}
          </p>
        )}
      </div>
    )
  }
)

Input.displayName = 'Input'
