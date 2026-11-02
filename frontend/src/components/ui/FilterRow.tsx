import { cn } from '../../utils/cn'

/**
 * FilterRow
 * ─────────
 * A row of small pills that filters a list already on screen (report status,
 * referral status, search tab). Distinct from `TabRow`, which switches between
 * *views* and gets the loud filled treatment.
 *
 * These pills are a filter, not navigation, so the selected state is a quiet
 * tinted chip rather than the brand gradient. They were previously styled as a
 * gradient-filled segmented control on six pages, which made a status filter
 * look like a primary action and competed with the page's real CTA.
 *
 * `aria-pressed` rather than `role="tab"`: these are toggles over one dataset,
 * not tabs over separate panels, and screen readers should hear "pressed".
 */
export function FilterRow<T extends string>({
  options,
  value,
  onChange,
  label,
  className,
}: {
  options: { value: T; label: string }[]
  value: T
  onChange: (v: T) => void
  /** Describes the filter for assistive tech, e.g. "Filter by status". */
  label: string
  className?: string
}) {
  return (
    <div className={cn('flex flex-wrap gap-1.5', className)} role="group" aria-label={label}>
      {options.map((o) => {
        const active = o.value === value
        return (
          <button
            key={o.value || 'all'}
            type="button"
            onClick={() => onChange(o.value)}
            aria-pressed={active}
            className={cn(
              'rounded-lg px-2.5 py-1.5 text-body-sm font-semibold transition-all',
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-campus-500',
              active
                ? 'bg-campus-100 text-campus-800'
                : 'text-surface-400 hover:bg-campus-50 hover:text-campus-900'
            )}
          >
            {o.label}
          </button>
        )
      })}
    </div>
  )
}
