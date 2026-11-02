import type { ReactNode } from 'react'
import { useInView } from '../../hooks/useInView'
import { useReducedMotion } from '../../hooks/useReducedMotion'
import { cn } from '../../utils/cn'

/**
 * Reveal
 * ──────
 * Fades and lifts its children the first time they scroll into view.
 *
 * This is the page-level counterpart to the `stagger` utility (which animates
 * a list on mount). Reach for Reveal when the content is *below the fold* and
 * the point is to introduce it as the reader arrives; use `stagger` for a list
 * that is already on screen when the page paints.
 *
 * Motion is strictly a progressive enhancement:
 *   • `prefers-reduced-motion` → children render immediately, static.
 *   • JavaScript disabled / no IntersectionObserver → the hook reports inView
 *     straight away, so nothing is ever hidden behind a script that didn't run.
 *
 * That last point matters: a reveal that gates *content* on JS is a broken page
 * for anyone without it. Only opacity and transform are animated here — the
 * element is always in the layout and always readable to a screen reader.
 */
export function Reveal({
  children,
  delay = 0,
  distance = 16,
  className,
  as: Tag = 'div',
}: {
  children: ReactNode
  /** Stagger in ms, e.g. `index * 60`. */
  delay?: number
  /** Pixels to travel on the y-axis. */
  distance?: number
  className?: string
  as?: 'div' | 'section' | 'article' | 'li'
}) {
  const reduced = useReducedMotion()
  const { ref, inView } = useInView<HTMLDivElement>()

  const shown = inView || reduced

  return (
    <Tag
      ref={ref as never}
      className={cn(className)}
      style={
        reduced
          ? undefined
          : {
              opacity: shown ? 1 : 0,
              transform: shown ? 'translateY(0)' : `translateY(${distance}px)`,
              transition: `opacity 620ms cubic-bezier(0.16, 1, 0.3, 1) ${delay}ms, transform 620ms cubic-bezier(0.16, 1, 0.3, 1) ${delay}ms`,
              willChange: shown ? undefined : 'opacity, transform',
            }
      }
    >
      {children}
    </Tag>
  )
}
