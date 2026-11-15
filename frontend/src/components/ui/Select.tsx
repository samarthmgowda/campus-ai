import { forwardRef, type SelectHTMLAttributes } from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '../../utils/cn'

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
  options: { value: string; label: string }[]
  placeholder?: string
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, options, placeholder, id, ...props }, ref) => {
    const selectId = id || label?.toLowerCase().replace(/\s+/g, '-')

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={selectId} className="block text-body-sm font-medium text-ink-200 mb-1.5">
            {label}
          </label>
        )}
        {/* `appearance-none` strips the OS arrow, which left the control with no
            affordance at all — it read as a text field. The chevron replaces it,
            and is pointer-events-none so it never swallows the click. */}
        <div className="relative">
          <select
            ref={ref}
            id={selectId}
            className={cn(
              'w-full rounded-lg border bg-er-900 px-3 py-2 pr-9 text-body text-ink-100 appearance-none',
              '[&>option]:bg-er-900 [&>option]:text-ink-100',
              'focus:outline-none focus:ring-2 focus:ring-er-500 focus:border-transparent',
              'disabled:bg-white/[0.03] disabled:text-ink-500 disabled:cursor-not-allowed',
              'transition-colors duration-150',
              error
                ? 'border-red-500/50 text-red-100 focus:ring-red-500'
                : 'border-white/12 hover:border-white/20',
              className
            )}
            aria-invalid={error ? 'true' : 'false'}
            aria-describedby={error ? `${selectId}-error` : undefined}
            {...props}
          >
            {placeholder && (
              <option value="" disabled>
                {placeholder}
              </option>
            )}
            {options.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <ChevronDown
            className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400"
            aria-hidden="true"
          />
        </div>
        {error && (
          <p id={`${selectId}-error`} className="mt-1.5 text-body-sm text-red-400" role="alert">
            {error}
          </p>
        )}
      </div>
    )
  }
)

Select.displayName = 'Select'
