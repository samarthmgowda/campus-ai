import { useEffect, useRef, useState } from 'react'

/**
 * Fires once when the element scrolls into view.
 *
 * Deliberately one-shot: re-animating on every scroll past makes a page feel
 * twitchy, and the point of a reveal is to introduce a section the first time
 * it is seen.
 */
export function useInView<T extends HTMLElement = HTMLDivElement>(
  rootMargin = '0px 0px -12% 0px'
) {
  const ref = useRef<T | null>(null)
  const [inView, setInView] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return

    if (typeof IntersectionObserver === 'undefined') {
      setInView(true)
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setInView(true)
            observer.disconnect()
          }
        }
      },
      { rootMargin, threshold: 0.15 }
    )

    observer.observe(el)
    return () => observer.disconnect()
  }, [rootMargin])

  return { ref, inView }
}
