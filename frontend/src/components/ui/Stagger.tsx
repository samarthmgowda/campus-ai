import { Children, cloneElement, isValidElement, type ReactNode } from 'react'
import { cn } from '../../utils/cn'

/**
 * Stagger
 * ───────
 * Introduces a list one item at a time, on mount.
 *
 * The CSS for this already existed (the `.stagger` utility plus a `--stagger`
 * custom property), but every call site had to remember two things in different
 * places to use it: the class on the parent, and a `style={{ ['--stagger' as
 * string]: Math.min(i, 8) }}` cast on each child. That cast appeared ~30 times,
 * and a handful of lists set the index but forgot the class (or the reverse),
 * so they simply did not animate.
 *
 * Doing it here means the index and the class are set together and cannot drift
 * apart. The cap keeps the last item from waiting a second and a half for its
 * turn on a long list.
 *
 * Motion is a progressive enhancement: the items are in the DOM and readable
 * regardless, and the global `prefers-reduced-motion` rule collapses the
 * animation to a no-op.
 */
export function Stagger({
  children,
  className,
  cap = 8,
  as: Tag = 'div',
}: {
  children: ReactNode
  className?: string
  /** Index at which the delay stops growing. */
  cap?: number
  as?: 'div' | 'ul' | 'ol' | 'section'
}) {
  return (
    <Tag className={cn('stagger', className)}>
      {Children.map(children, (child, i) =>
        isValidElement(child)
          ? cloneElement(child as React.ReactElement<{ style?: React.CSSProperties }>, {
              style: {
                ...(child.props as { style?: React.CSSProperties }).style,
                ['--stagger' as string]: Math.min(i, cap),
              },
            })
          : child
      )}
    </Tag>
  )
}
