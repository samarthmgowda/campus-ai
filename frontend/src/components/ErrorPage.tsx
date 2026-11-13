import {
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type ComponentType,
} from 'react'
import { Layers, ArrowLeft, ChevronDown, type LucideIcon } from 'lucide-react'
import { useReducedMotion } from '../hooks/useReducedMotion'
import '../styles/error.css'

/**
 * One error surface for the whole app.
 *
 * There used to be three unrelated ones — a light-themed 404, a light-themed
 * boundary, and nothing at all for a dead backend — that shared nothing with
 * each other or with the product. This replaces all of them with a single
 * screen in the Campus Connect palette.
 *
 * It is deliberately *router-agnostic*: the class-based ErrorBoundary sits
 * outside BrowserRouter, so this component cannot call useLocation or render a
 * <Link>. Callers inside the router pass `linkAs`, callers outside get a plain
 * anchor and a full navigation. Same markup either way.
 */

export type ErrorKind = 'not-found' | 'crash' | 'offline' | 'forbidden'

export interface TraceRow {
  label: string
  value: ReactNode
}

export interface ErrorAction {
  label: string
  /** In-app destination. Ignored when `onClick` is given. */
  to?: string
  onClick?: () => void
  icon?: LucideIcon
  disabled?: boolean
}

export interface RecoveryItem {
  path: string
  label: string
  hint?: string
  icon: LucideIcon
}

export interface ErrorPageProps {
  kind: ErrorKind
  /** The loud numeral. "404", "500", "502". */
  code: string
  eyebrow: string
  title: string
  body: ReactNode

  /** The product's own idiom: report what was looked for and what came back. */
  traceTitle?: string
  traceRows?: TraceRow[]
  /** Folded-away stack trace or raw cause. Never shown open. */
  detail?: string

  primary: ErrorAction
  secondary?: ErrorAction
  /** Small mono line under the actions — a countdown, a retry state. */
  hint?: ReactNode

  recoverLabel?: string
  recover?: RecoveryItem[]

  footLinks?: { label: string; to: string }[]
  footNote?: string

  /** Router <Link>, injected by callers that live inside the router. */
  linkAs?: ComponentType<{
    to: string
    className?: string
    children: ReactNode
    onClick?: () => void
  }>

  /** Move focus to the headline on mount. On for routed errors, off while a
   *  crash is being recovered so focus is not stolen mid-interaction. */
  autoFocusTitle?: boolean
}

const DEFAULT_FOOT_LINKS = [
  { label: 'Home', to: '/' },
  { label: 'Sign in', to: '/login' },
  { label: 'Search', to: '/search' },
  { label: 'Community', to: '/community' },
]

export default function ErrorPage({
  kind,
  code,
  eyebrow,
  title,
  body,
  traceTitle = 'Retrieval trace',
  traceRows,
  detail,
  primary,
  secondary,
  hint,
  recoverLabel = 'Where you can go instead',
  recover,
  footLinks = DEFAULT_FOOT_LINKS,
  footNote = 'A demonstration project for MIT Mysore. Answers are grounded in curated campus documents.',
  linkAs,
  autoFocusTitle = true,
}: ErrorPageProps) {
  const reduced = useReducedMotion()
  const titleRef = useRef<HTMLHeadingElement>(null)
  const [ready, setReady] = useState(false)

  // One entrance for the page, not a fade per block: two frames so the initial
  // styles are painted before the transition target is applied.
  useEffect(() => {
    if (reduced) {
      setReady(true)
      return
    }
    let inner = 0
    const outer = requestAnimationFrame(() => {
      inner = requestAnimationFrame(() => setReady(true))
    })
    return () => {
      cancelAnimationFrame(outer)
      if (inner) cancelAnimationFrame(inner)
    }
  }, [reduced])

  useEffect(() => {
    if (autoFocusTitle && titleRef.current) titleRef.current.focus()
  }, [autoFocusTitle])

  const Link = linkAs
  const renderLink = (to: string, className: string, children: ReactNode, onClick?: () => void) =>
    Link ? (
      <Link to={to} className={className} onClick={onClick}>
        {children}
      </Link>
    ) : (
      <a href={to} className={className} onClick={onClick}>
        {children}
      </a>
    )

  const renderAction = (action: ErrorAction, variant: 'primary' | 'ghost') => {
    const Icon = action.icon
    const className = `er-btn er-btn--${variant}`
    const inner = (
      <>
        {Icon && <Icon className="h-4 w-4" aria-hidden="true" />}
        {action.label}
      </>
    )

    if (action.onClick) {
      return (
        <button
          type="button"
          onClick={action.onClick}
          className={className}
          disabled={action.disabled}
          aria-disabled={action.disabled || undefined}
        >
          {inner}
        </button>
      )
    }
    return renderLink(action.to ?? '/', className, inner)
  }

  return (
    <div className={`er er--${kind}`} data-ready={ready ? 'true' : 'false'}>
      <header className="er-head">
        <div className="er-shell er-head-inner">
          <a href="/" className="er-brand" aria-label="Campus Connect home">
            <span className="er-brand-mark" aria-hidden="true">
              <Layers className="h-4 w-4" strokeWidth={2.25} />
            </span>
            <span>Campus Connect</span>
          </a>

          <span className="er-head-context" aria-hidden="true">
            / campus-ai
          </span>

          <div className="er-head-actions">
            <a href="/" className="er-btn er-btn--ghost er-btn--sm">
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Home
            </a>
          </div>
        </div>
      </header>

      <main className="er-main">
        <div className="er-shell">
          <div className="er-grid">
            <div className="er-copy">
              <p className="er-eyebrow er-anim">
                <span className="er-eyebrow-dot" aria-hidden="true" />
                {eyebrow}
              </p>

              <div className="er-code er-anim er-anim--code" aria-hidden="true">
                {code}
              </div>

              <h1 className="er-title er-anim" ref={titleRef} tabIndex={-1}>
                {title}
              </h1>

              <p className="er-body er-anim">{body}</p>

              <div className="er-actions er-anim">
                {renderAction(primary, 'primary')}
                {secondary && renderAction(secondary, 'ghost')}
              </div>

              {hint && <p className="er-hint er-anim">{hint}</p>}
            </div>

            {traceRows && traceRows.length > 0 && (
              <div className="er-anim er-anim--panel">
                <div className="er-trace">
                  <p className="er-trace-head">{traceTitle}</p>
                  <dl className="er-rows">
                    {traceRows.map((row) => (
                      <div className="er-row" key={row.label}>
                        <dt>{row.label}</dt>
                        <dd>{row.value}</dd>
                      </div>
                    ))}
                  </dl>

                  {detail && (
                    <details className="er-detail">
                      <summary>
                        <ChevronDown className="h-3 w-3" aria-hidden="true" />
                        Technical detail
                      </summary>
                      <pre>{detail}</pre>
                    </details>
                  )}
                </div>
              </div>
            )}
          </div>

          {recover && recover.length > 0 && (
            <nav className="er-recover" aria-label="Suggested pages">
              <p className="er-recover-label">{recoverLabel}</p>
              <div className="er-cards">
                {recover.map((item) => {
                  const Icon = item.icon
                  return (
                    <div key={item.path}>
                      {renderLink(
                        item.path,
                        'er-card',
                        <>
                          <span className="er-card-icon" aria-hidden="true">
                            <Icon className="h-4 w-4" />
                          </span>
                          <span className="er-card-body">
                            <span className="er-card-label">{item.label}</span>
                            {item.hint && <span className="er-card-hint">{item.hint}</span>}
                          </span>
                        </>
                      )}
                    </div>
                  )
                })}
              </div>
            </nav>
          )}
        </div>
      </main>

      <footer className="er-foot">
        <div className="er-shell er-foot-inner">
          <nav className="er-foot-links" aria-label="Footer">
            {footLinks.map((link) => (
              <span key={link.to}>{renderLink(link.to, '', link.label)}</span>
            ))}
          </nav>
          <p className="er-foot-note">{footNote}</p>
        </div>
      </footer>
    </div>
  )
}
