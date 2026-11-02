import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle, RotateCcw, Home, ChevronDown } from 'lucide-react'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
  showDetail: boolean
}

/**
 * Catches render-time crashes so a single broken page cannot take the whole app
 * down to a blank white screen.
 *
 * The person is told what happened in plain language and given two ways out. The
 * technical detail is available but folded away, because a stack trace is useful
 * to a developer and only alarming to everyone else.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null, showDetail: false }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Kept as a console error so it survives in the browser's log for debugging.
    console.error('Unhandled UI error:', error, info.componentStack)
  }

  private reset = () => {
    this.setState({ error: null, showDetail: false })
  }

  private reload = () => {
    window.location.href = '/'
  }

  render() {
    const { error, showDetail } = this.state
    if (!error) return this.props.children

    return (
      <div className="flex min-h-dvh items-center justify-center bg-[#f5f7fa] px-5 py-16">
        <div className="w-full max-w-xl">
          <span className="inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-red-100 bg-white shadow-card">
            <AlertTriangle className="h-6 w-6 text-red-500" aria-hidden="true" />
          </span>

          <h1 className="mt-6 font-display font-bold tracking-[-0.02em] text-[clamp(1.75rem,4vw,2.25rem)] leading-tight text-campus-950">
            Something broke on this page.
          </h1>
          <p className="mt-4 max-w-[52ch] text-body leading-relaxed text-surface-600">
            The rest of the app is fine — this screen hit an error while rendering. You can try it again, or head
            back to somewhere that works.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <button
              onClick={this.reset}
              className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] px-5 py-2.5 text-body-sm font-semibold text-white shadow-card transition-transform duration-150 hover:-translate-y-[2px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500 focus-visible:ring-offset-2"
            >
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              Try again
            </button>
            <button
              onClick={this.reload}
              className="inline-flex items-center gap-2 rounded-xl border border-campus-900/15 bg-white px-5 py-2.5 text-body-sm font-semibold text-campus-900 transition-colors hover:border-campus-900/30 hover:bg-campus-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-500"
            >
              <Home className="h-4 w-4" aria-hidden="true" />
              Back to start
            </button>
          </div>

          <div className="mt-10 border-t border-campus-900/[0.08] pt-5">
            <button
              onClick={() => this.setState((s) => ({ showDetail: !s.showDetail }))}
              aria-expanded={showDetail}
              className="inline-flex items-center gap-1.5 text-body-sm font-medium text-surface-500 transition-colors hover:text-campus-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-campus-400 rounded-lg px-1 py-1"
            >
              <ChevronDown className={`h-4 w-4 transition-transform ${showDetail ? 'rotate-180' : ''}`} aria-hidden="true" />
              Technical details
            </button>
            {showDetail && (
              <pre className="mt-3 max-h-56 overflow-auto rounded-xl border border-campus-900/[0.08] bg-white p-4 font-mono text-micro leading-relaxed text-surface-600 whitespace-pre-wrap break-words">
                {error.message}
                {error.stack ? `\n\n${error.stack}` : ''}
              </pre>
            )}
          </div>
        </div>
      </div>
    )
  }
}
