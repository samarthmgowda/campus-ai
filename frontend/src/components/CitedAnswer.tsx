import type { ChatSource } from '../types'
import { cn } from '../utils/cn'

/**
 * Renders an answer with [n] citation markers as clickable chips.
 * Each chip scrolls to + flashes the matching source chip below.
 * React escapes all text by default — no innerHTML, no injection risk.
 */
export function CitedAnswer({
  text,
  sources,
  idPrefix,
}: {
  text: string
  sources?: ChatSource[]
  idPrefix: string
}) {
  const count = sources?.length ?? 0

  function jumpTo(n: number) {
    const el = document.getElementById(`${idPrefix}-source-${n}`)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    el.animate(
      [
        { boxShadow: '0 0 0 3px rgba(118,75,162,0.55)' },
        { boxShadow: '0 0 0 0 rgba(118,75,162,0)' },
      ],
      { duration: 900 }
    )
  }

  // Split keeping [n] delimiters; plain strings stay as text nodes.
  const parts = (text ?? '').split(/(\[\d+\])/g)

  return (
    <p className="whitespace-pre-wrap text-body-sm leading-relaxed text-surface-800">
      {parts.map((part, i) => {
        const m = /^\[(\d+)\]$/.exec(part)
        if (!m) return <span key={i}>{part}</span>
        const n = parseInt(m[1], 10)
        if (n < 1 || n > count) return <span key={i}>{part}</span>
        const title = sources?.[n - 1]?.documentTitle ?? `Source ${n}`
        return (
          <button
            key={i}
            type="button"
            onClick={() => jumpTo(n)}
            title={title}
            aria-label={`Citation ${n}: ${title}`}
            className={cn(
              'mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded-md px-1 align-baseline',
              'bg-campus-100 text-campus-700 text-micro font-bold tabular-nums',
              'hover:bg-campus-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500'
            )}
          >
            {n}
          </button>
        )
      })}
    </p>
  )
}
