import { useEffect, useState } from 'react'

/**
 * True when the visitor has asked their system to reduce motion.
 *
 * Motion is a progressive enhancement here: when this is true, animated
 * elements should still be *present* and legible, just static. Never use it to
 * hide content, or the page breaks for the people who asked for less motion.
 */
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  })

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
    const onChange = () => setReduced(mq.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  return reduced
}
