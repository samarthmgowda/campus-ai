import { useEffect, useState } from 'react'
import { cn } from '../../utils/cn'
import { useReducedMotion } from '../../hooks/useReducedMotion'
import { Users, Check } from 'lucide-react'

/**
 * The hero's centrepiece: a teammate request filling up with matched peers.
 *
 * This replaces the old chat demo on purpose. The product's differentiator is
 * not that it answers questions — it is that it finds the right person on your
 * own campus. So the hero shows a team forming, which is the thing a student
 * actually came here to do.
 */

const REQUEST = {
  event: 'AutoHack 2026',
  role: 'UI lead',
  seats: 4,
  filled: 1,
  skills: ['React', 'Figma', 'Design systems'],
}

const CANDIDATES = [
  { name: 'Ananya R.', meta: '3rd yr · CSE', match: 94, skill: 'React · Figma', initial: 'A' },
  { name: 'Karthik M.', meta: '2nd yr · ISE', match: 88, skill: 'React · Storybook', initial: 'K' },
  { name: 'Sneha P.', meta: '4th yr · CSE', match: 81, skill: 'Figma · Prototyping', initial: 'S' },
]

export default function PeerMatchDemo() {
  const reduced = useReducedMotion()
  const [shown, setShown] = useState(reduced ? CANDIDATES.length : 0)

  useEffect(() => {
    if (reduced) {
      setShown(CANDIDATES.length)
      return
    }
    const timers: number[] = []
    CANDIDATES.forEach((_, i) => {
      timers.push(window.setTimeout(() => setShown(i + 1), 900 + i * 750))
    })
    return () => timers.forEach(clearTimeout)
  }, [reduced])

  const seatsFilled = REQUEST.filled + shown

  return (
    <div className="w-full max-w-[460px]">
      {/* The request */}
      <div className="rounded-2xl border border-er-700/40 bg-er-900 p-5 shadow-card">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="font-mono text-micro uppercase tracking-wide text-ink-400">Team request</p>
            <p className="mt-1.5 font-display text-heading-sm font-semibold text-ink-50">
              {REQUEST.event}
            </p>
          </div>
          <span className="flex-shrink-0 rounded-full border border-er-600/40 bg-er-800 px-2.5 py-1 font-mono text-micro font-medium text-ink-300">
            {seatsFilled} / {REQUEST.seats}
          </span>
        </div>

        <p className="mt-3 text-body-sm text-ink-300">
          Needs a <span className="font-semibold text-ink-50">{REQUEST.role}</span> for the build sprint.
        </p>

        <div className="mt-3.5 flex flex-wrap gap-1.5">
          {REQUEST.skills.map((s) => (
            <span
              key={s}
              className="rounded-md border border-er-700/50 bg-er-800/60 px-2 py-0.5 font-mono text-micro text-ink-300"
            >
              {s}
            </span>
          ))}
        </div>
      </div>

      {/* The matches */}
      <div className="mt-3 space-y-2">
        {CANDIDATES.map((c, i) => {
          const visible = i < shown
          return (
            <div
              key={c.name}
              className={cn(
                'flex items-center gap-3 rounded-xl border bg-er-900/80 px-3.5 py-3 transition-all duration-500 ease-out-expo',
                visible
                  ? 'border-er-600/40 opacity-100 translate-y-0'
                  : 'border-er-800/60 opacity-0 translate-y-2'
              )}
            >
              <span
                className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full border border-er-600/40 bg-er-800 font-display text-body-sm font-bold text-ink-200"
                aria-hidden="true"
              >
                {c.initial}
              </span>

              <span className="min-w-0 flex-1">
                <span className="block truncate text-body-sm font-medium text-ink-50">{c.name}</span>
                <span className="block truncate text-micro text-ink-400">
                  {c.meta} · {c.skill}
                </span>
              </span>

              <span className="flex flex-shrink-0 items-center gap-1.5">
                <span className="font-mono text-body-sm font-semibold text-ink-200">{c.match}%</span>
                <span
                  className={cn(
                    'flex h-5 w-5 items-center justify-center rounded-full transition-colors duration-300',
                    visible ? 'bg-er-500/15 text-ink-200' : 'bg-er-800 text-er-700'
                  )}
                  aria-hidden="true"
                >
                  <Check className="h-3 w-3" />
                </span>
              </span>
            </div>
          )
        })}
      </div>

      <p className="mt-3.5 flex items-center gap-2 text-caption text-ink-400">
        <Users className="h-3.5 w-3.5 flex-shrink-0 text-ink-400" aria-hidden="true" />
        Matched from verified MIT Mysore profiles
      </p>
    </div>
  )
}
