import { NAV_GROUPS, PAGE_META } from '../config/navigation'

/**
 * "Did you mean…?" for a mistyped URL.
 *
 * A 404 that only says "not found" is a dead end. Most bad URLs inside an app
 * are a near miss on a real one — `/project` for `/projects`, `/dashbord` for
 * `/dashboard` — so the page can name the destination the visitor was probably
 * reaching for.
 *
 * Deliberately conservative: two edits at most, and never for a path in a
 * different top-level section, because suggesting `/events` to someone who
 * typed `/admin/report` is worse than saying nothing.
 */

export interface RouteSuggestion {
  path: string
  label: string
}

/** Every client-side route worth suggesting. Parametric routes are excluded —
 *  offering `/events/:id` to a human is noise. */
function knownRoutes(): RouteSuggestion[] {
  const paths = new Set<string>(['/', '/login', '/chat/full'])
  for (const group of NAV_GROUPS) {
    for (const item of group.items) paths.add(item.path)
  }
  for (const path of Object.keys(PAGE_META)) paths.add(path)

  return Array.from(paths)
    .filter((p) => !p.includes(':'))
    .map((path) => ({
      path,
      label: PAGE_META[path]?.title ?? (path === '/' ? 'Home' : path.replace('/', '')),
    }))
}

/** Levenshtein distance, iterative with two rows — paths are short, so the
 *  O(n·m) table is trivial and there is no reason to be clever about it. */
function distance(a: string, b: string): number {
  if (a === b) return 0
  if (!a.length) return b.length
  if (!b.length) return a.length

  let prev = new Array<number>(b.length + 1)
  let curr = new Array<number>(b.length + 1)
  for (let j = 0; j <= b.length; j++) prev[j] = j

  for (let i = 1; i <= a.length; i++) {
    curr[0] = i
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1
      curr[j] = Math.min(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
    }
    const swap = prev
    prev = curr
    curr = swap
  }
  return prev[b.length]
}

/** Normalise a path for comparison: trim slashes, lowercase, drop a trailing
 *  file-ish segment so `/projects/123` compares against `/projects`. */
function normalise(path: string): string {
  const clean = path.toLowerCase().replace(/\/+$/, '')
  return clean === '' ? '/' : clean
}

/** The single best real route for a mistyped one, or null when nothing is
 *  close enough to be a useful guess. */
export function suggestRoute(pathname: string): RouteSuggestion | null {
  const target = normalise(pathname)
  if (target === '/') return null

  const targetSegments = target.split('/').filter(Boolean)
  const targetHead = targetSegments[0] ?? ''

  let best: RouteSuggestion | null = null
  let bestScore = Infinity

  for (const route of knownRoutes()) {
    const candidate = normalise(route.path)
    if (candidate === target) return null // the route exists; not a 404

    // Only suggest within the same top-level section, so a typo in /projects
    // never recommends /admin. A one-segment typo has no section to match on,
    // so fall back to comparing the whole path.
    const candidateHead = candidate.split('/').filter(Boolean)[0] ?? ''
    if (targetSegments.length > 1 && candidateHead !== targetHead) continue

    // Compare like for like: a deep path against a deeper route.
    const a = targetSegments.length > 1 ? target : target.replace(/^\//, '')
    const b = candidate.split('/').filter(Boolean).join('/')
    if (!b) continue

    let score = distance(a, b)

    // A shared prefix is strong evidence ("/proj" → "/projects").
    const shared = [...a].findIndex((ch, i) => ch !== b[i])
    if (shared >= 3) score -= 1
    if (shared === -1) score -= 2

    if (score < bestScore) {
      bestScore = score
      best = route
    }
  }

  // Two edits is generous for a real typo and strict enough that unrelated
  // words do not get recommended to each other.
  const limit = Math.max(1, Math.min(2, Math.floor(target.replace(/^\//, '').length / 3)))
  return bestScore <= limit ? best : null
}
