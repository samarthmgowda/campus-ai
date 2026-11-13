import { Component, type ErrorInfo, type ReactNode } from 'react'
import { RotateCcw, Home } from 'lucide-react'
import ErrorPage from './ErrorPage'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/**
 * Catches render-time crashes so a single broken page cannot take the whole app
 * down to a blank white screen.
 *
 * The person is told what happened in plain language and given two ways out. The
 * technical detail is available but folded away, because a stack trace is useful
 * to a developer and only alarming to everyone else.
 *
 * It sits *outside* BrowserRouter, so it cannot render a router <Link>; the
 * ErrorPage falls back to plain anchors for exactly that reason.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Kept as a console error so it survives in the browser's log for debugging.
    console.error('Unhandled UI error:', error, info.componentStack)
  }

  private reset = () => {
    this.setState({ error: null })
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children

    return (
      <ErrorPage
        kind="crash"
        code="500"
        eyebrow="Screen crashed"
        title="This screen hit an error."
        body={
          <>
            The rest of the app is still fine — this page failed while rendering
            and was stopped before it could take anything else down with it.
          </>
        }
        traceTitle="Crash detail"
        traceRows={[
          { label: 'type', value: error.name || 'Error' },
          { label: 'message', value: error.message || 'no message' },
          { label: 'scope', value: 'this screen only' },
        ]}
        detail={error.stack}
        primary={{ label: 'Try again', icon: RotateCcw, onClick: this.reset }}
        secondary={{ label: 'Back to start', icon: Home, to: '/' }}
        // Focus is deliberately not stolen here: the person may be mid-recovery.
        autoFocusTitle={false}
      />
    )
  }
}
