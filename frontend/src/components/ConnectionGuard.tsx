import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { RotateCw, RefreshCw } from 'lucide-react'
import ErrorPage, { type TraceRow } from './ErrorPage'
import { CONNECTION_LOST_EVENT, CONNECTION_RESTORED_EVENT } from '../api/client'

/**
 * Takes over the screen when the backend stops answering.
 *
 * Without this, a dead server surfaces as a different red toast on every panel
 * that happened to be fetching, which reads as "everything is broken in many
 * ways" rather than "the server is down". One screen, one explanation, one
 * retry.
 *
 * It probes /health rather than reloading blindly: a reload while the server is
 * still starting would show the same screen again and lose whatever the person
 * had typed.
 */

const PROBE_TIMEOUT_MS = 4000

async function probe(): Promise<boolean> {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS)
  try {
    const res = await fetch('/health', {
      method: 'GET',
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal: controller.signal,
    })
    return res.ok
  } catch {
    return false
  } finally {
    window.clearTimeout(timer)
  }
}

export default function ConnectionGuard({ children }: { children: ReactNode }) {
  const [lost, setLost] = useState(false)
  const [retrying, setRetrying] = useState(false)
  const [secondsAgo, setSecondsAgo] = useState(0)
  const lostAt = useRef<number>(0)

  useEffect(() => {
    const onLost = () => {
      if (lost) return
      lostAt.current = Date.now()
      setSecondsAgo(0)
      setLost(true)
    }
    const onRestored = () => setLost(false)
    window.addEventListener(CONNECTION_LOST_EVENT, onLost)
    window.addEventListener(CONNECTION_RESTORED_EVENT, onRestored)
    return () => {
      window.removeEventListener(CONNECTION_LOST_EVENT, onLost)
      window.removeEventListener(CONNECTION_RESTORED_EVENT, onRestored)
    }
  }, [lost])

  // Tick a "last checked" counter so the screen is visibly alive rather than a
  // static failure message.
  useEffect(() => {
    if (!lost) return
    const id = window.setInterval(() => {
      setSecondsAgo(Math.round((Date.now() - lostAt.current) / 1000))
    }, 1000)
    return () => window.clearInterval(id)
  }, [lost])

  const retry = useCallback(async () => {
    setRetrying(true)
    const ok = await probe()
    setRetrying(false)
    if (ok) {
      // The server is back but the in-memory app may be holding failed queries.
      // A reload is the only honest way to resync every panel at once.
      window.location.reload()
    } else {
      lostAt.current = Date.now()
      setSecondsAgo(0)
    }
  }, [])

  // Try once automatically when the browser reports it is back online —
  // that is the one case where a retry is very likely to succeed.
  useEffect(() => {
    if (!lost) return
    const onOnline = () => void retry()
    window.addEventListener('online', onOnline)
    return () => window.removeEventListener('online', onOnline)
  }, [lost, retry])

  if (!lost) return <>{children}</>

  const traceRows: TraceRow[] = [
    { label: 'endpoint', value: 'GET /health' },
    { label: 'result', value: <span className="is-empty">no response</span> },
    { label: 'checked', value: secondsAgo === 0 ? 'just now' : `${secondsAgo}s ago` },
    {
      label: 'cause',
      value: <span className="is-empty">API process not answering</span>,
    },
  ]

  return (
    <ErrorPage
      kind="offline"
      code="502"
      eyebrow="Server unreachable"
      title="Campus Connect cannot reach its server."
      body={
        <>
          The app is fine, but the API behind it is not answering. This usually
          fixes itself — the backend may be restarting. Nothing you were looking
          at has been lost.
        </>
      }
      traceTitle="Connection trace"
      traceRows={traceRows}
      primary={{ label: retrying ? 'Checking…' : 'Try again', icon: RotateCw, onClick: () => void retry(), disabled: retrying }}
      secondary={{ label: 'Reload the page', icon: RefreshCw, onClick: () => window.location.reload() }}
      hint={
        <>
          <span className={`er-hint-dot${retrying ? ' er-hint-dot--pulse' : ''}`} aria-hidden="true" />
          {retrying ? 'Probing /health…' : 'Retries automatically when your network returns'}
        </>
      }
      // No recovery grid: every in-app destination needs the same API that is
      // down, so offering links here would be four more dead ends.
      footNote="Your session is stored in this browser, so nothing was lost — reloading picks up exactly where you were."
    />
  )
}
