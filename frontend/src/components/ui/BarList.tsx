import { cn } from '../../utils/cn'

/**
 * The bar fills, in order. Repeats if there are more rows than colours.
 *
 * The first two are both electric blue — the top of a ranking is where the eye
 * lands, so it gets the brand; the rest fan out into the secondary hues so a
 * long list stays separable. They are all ≥400 on midnight so a 2px-tall bar
 * still reads against the track.
 */
const BAR_FILLS = [
  'from-er-400 to-er-500',
  'from-er-500 to-er-600',
  'from-sky-400 to-sky-500',
  'from-emerald-400 to-emerald-500',
  'from-orange-400 to-orange-500',
]

export interface BarDatum {
  label: string
  value: number
}

/**
 * BarList
 * ───────
 * A horizontal bar per row, for a ranking over a single measure (top skills,
 * students per branch). A list, not a chart: each row is labelled and carries
 * its own value, so the reader never has to trace a legend.
 *
 * Its job includes the accessibility work a hand-rolled bar misses — a `role`
 * on the bar so the value is announced with its label, `min-width` so a tiny
 * count still shows something rather than an invisible sliver, and tabular
 * numbers so the value column stays aligned down the list.
 *
 * `max` is injectable because callers comparing two lists against a common
 * ceiling (e.g. percentage-of-total) need the bars to share a scale.
 */
export function BarList({
  data,
  max,
  limit = 8,
  className,
  emptyLabel = 'No data yet.',
}: {
  data: BarDatum[]
  max?: number
  limit?: number
  className?: string
  emptyLabel?: string
}) {
  if (data.length === 0) {
    return <p className="py-4 text-center text-caption text-ink-500">{emptyLabel}</p>
  }

  const ceiling = max ?? Math.max(...data.map((d) => d.value), 1)

  return (
    <div className={cn('space-y-2.5', className)} role="list" aria-label="Ranked values">
      {data.slice(0, limit).map((d, i) => (
        <div key={d.label} className="flex items-center gap-3" role="listitem">
          <span
            className="w-28 flex-shrink-0 truncate text-body-sm font-medium text-ink-200"
            title={d.label}
          >
            {d.label}
          </span>
          <span
            className="h-2 flex-1 overflow-hidden rounded-full bg-white/[0.07]"
            role="img"
            aria-label={`${d.label}: ${d.value}`}
          >
            <span
              className={cn(
                'block h-full rounded-full bg-gradient-to-r transition-all duration-500',
                BAR_FILLS[i % BAR_FILLS.length]
              )}
              style={{ width: `${Math.max(5, (d.value / ceiling) * 100)}%` }}
              aria-hidden="true"
            />
          </span>
          <span className="w-10 flex-shrink-0 text-right text-body-sm font-semibold tabular-nums text-ink-100">
            {d.value}
          </span>
        </div>
      ))}
    </div>
  )
}

/** Turn a `Record<string, number>` or an array of rows into `BarDatum[]`. */
export function toBarData(
  data: Record<string, number> | Record<string, unknown>[],
  labelKey = 'skill',
  valueKey = 'count'
): BarDatum[] {
  if (Array.isArray(data)) {
    return data.map((r) => ({
      label: String(r[labelKey] ?? r.name ?? '?'),
      value: Number(r[valueKey] ?? r.studentCount ?? 0),
    }))
  }
  return Object.entries(data).map(([label, value]) => ({ label, value: Number(value) }))
}
