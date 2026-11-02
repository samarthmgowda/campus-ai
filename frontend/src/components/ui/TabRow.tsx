import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

export interface TabItem<T extends string = string> {
  id: T
  label: string
  /** Optional count rendered after the label, e.g. "People 12". */
  count?: number
  icon?: ReactNode
}

/**
 * TabRow
 * ──────
 * A segmented control for switching between views of the same data.
 *
 * Implemented with real ARIA tab semantics (`role="tablist"` / `"tab"` /
 * `aria-selected`) and full keyboard support — arrow keys move between tabs,
 * Home/End jump to the ends. These are cosmetic-looking but they are the
 * difference between a control a keyboard user can operate and one they can
 * only tab past.
 *
 * The underlying value stays controlled by the caller, so this is a drop-in
 * for the `const [tab, setTab] = useState(...)` pattern the pages already use.
 *
 * Note on ARIA: `role="tab"` technically promises a `tabpanel` sibling. Pages
 * here render their panels outside this component, so we also set
 * `aria-controls` when an `id` is supplied and let callers wire `role="tabpanel"`
 * on the panel itself for full correctness.
 */
export function TabRow<T extends string>({
  tabs,
  value,
  onChange,
  size = 'md',
  className,
  ariaLabel = 'View',
  idPrefix,
}: {
  tabs: TabItem<T>[]
  value: T
  onChange: (id: T) => void
  size?: 'sm' | 'md'
  className?: string
  ariaLabel?: string
  idPrefix?: string
}) {
  const move = (dir: 1 | -1 | 'first' | 'last') => {
    const i = tabs.findIndex((t) => t.id === value)
    if (i === -1) return
    let next: number
    if (dir === 'first') next = 0
    else if (dir === 'last') next = tabs.length - 1
    else next = (i + dir + tabs.length) % tabs.length
    onChange(tabs[next].id)
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        e.preventDefault()
        move(1)
        break
      case 'ArrowLeft':
      case 'ArrowUp':
        e.preventDefault()
        move(-1)
        break
      case 'Home':
        e.preventDefault()
        move('first')
        break
      case 'End':
        e.preventDefault()
        move('last')
        break
    }
  }

  const pad = size === 'sm' ? 'px-3 py-1.5 text-caption' : 'px-3.5 py-2 text-body-sm'

  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      onKeyDown={onKeyDown}
      className={cn(
        'inline-flex items-center gap-1 rounded-2xl border border-campus-900/[0.07] bg-white p-1 shadow-card',
        className
      )}
    >
      {tabs.map((t) => {
        const selected = t.id === value
        return (
          <button
            key={t.id}
            role="tab"
            type="button"
            id={idPrefix ? `${idPrefix}-tab-${t.id}` : undefined}
            aria-controls={idPrefix ? `${idPrefix}-panel-${t.id}` : undefined}
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(t.id)}
            className={cn(
              'relative inline-flex items-center gap-1.5 rounded-xl font-semibold transition-all',
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-campus-500',
              pad,
              selected
                ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white shadow-card'
                : 'text-surface-500 hover:text-campus-900 hover:bg-campus-50'
            )}
          >
            {t.icon}
            {t.label}
            {typeof t.count === 'number' && (
              <span
                className={cn(
                  'tabular-nums text-micro rounded-full px-1.5 min-w-[18px] text-center',
                  selected ? 'bg-white/20 text-white' : 'bg-campus-900/[0.06] text-surface-500'
                )}
              >
                {t.count}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}

/**
 * ScrollTabs
 * ──────────
 * A tab row for narrow screens, where a segmented control would overflow.
 *
 * Renders the same ARIA contract as TabRow but as a horizontally scrollable
 * strip with an edge fade, so a phone user can swipe rather than having tabs
 * silently clipped off the right edge.
 */
export function ScrollTabs<T extends string>({
  tabs,
  value,
  onChange,
  className,
  ariaLabel = 'View',
}: {
  tabs: TabItem<T>[]
  value: T
  onChange: (id: T) => void
  className?: string
  ariaLabel?: string
}) {
  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className={cn(
        'flex items-center gap-1.5 overflow-x-auto pb-1 -mb-1 scrollbar-light',
        className
      )}
    >
      {tabs.map((t) => {
        const selected = t.id === value
        return (
          <button
            key={t.id}
            role="tab"
            type="button"
            aria-selected={selected}
            onClick={() => onChange(t.id)}
            className={cn(
              'whitespace-nowrap rounded-full px-3.5 py-1.5 text-body-sm font-semibold transition-all flex-shrink-0',
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-campus-500',
              selected
                ? 'bg-campus-900 text-white'
                : 'bg-white border border-campus-900/10 text-surface-500 hover:text-campus-900'
            )}
          >
            {t.label}
            {typeof t.count === 'number' && (
              <span className="ml-1.5 tabular-nums opacity-60">{t.count}</span>
            )}
          </button>
        )
      })}
    </div>
  )
}
