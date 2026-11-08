import { useEffect, useRef } from 'react'
import type { ChatSource, ChatTurn, AgentResponse } from '../../types'
import { CitedAnswer } from '../CitedAnswer'
import { EntityCards } from '../ui/EntityCards'
import { Bot, Loader2, AlertTriangle, BookOpen, ExternalLink } from 'lucide-react'
import { cn } from '../../utils/cn'

function formatTime(dateString: string) {
  try {
    return new Date(dateString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

function Sources({ sources, idPrefix }: { sources?: ChatSource[]; idPrefix: string }) {
  if (!sources || sources.length === 0) return null
  return (
    <div className="flex items-center gap-1.5 flex-wrap pt-2.5 mt-1 border-t border-campus-900/[0.07]">
      <span className="inline-flex items-center gap-1 text-micro font-medium text-surface-400">
        <BookOpen className="h-3.5 w-3.5" aria-hidden="true" /> Grounded in
      </span>
      {sources.map((s, i) => (
        <span
          key={i}
          id={`${idPrefix}-source-${i + 1}`}
          className="inline-flex items-center gap-1 rounded-full bg-campus-50 border border-campus-100 px-2.5 py-1 text-micro font-medium text-campus-700 scroll-mt-2"
        >
          <ExternalLink className="h-3 w-3" aria-hidden="true" />
          {s.documentTitle}
          {s.section && <span className="text-campus-400">· {s.section}</span>}
          {s.pageNumber != null && <span className="text-campus-400">· p.{s.pageNumber}</span>}
        </span>
      ))}
    </div>
  )
}

/**
 * Shared message transcript for the Campus AI conversation. Renders persisted
 * turns, any in-flight question, transport errors and the just-received answer.
 * `compact` tightens the spacing for the small floating widget; the full page
 * uses the roomier default.
 */
export function ChatThread({
  history,
  activeAsk,
  isPending,
  error,
  liveResponse,
  compact = false,
}: {
  history: ChatTurn[]
  activeAsk: string | null
  isPending: boolean
  error: Error | null
  liveResponse: AgentResponse | null
  compact?: boolean
}) {
  const endRef = useRef<HTMLDivElement>(null)
  const gap = compact ? 'gap-4' : 'gap-5'

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [history.length, liveResponse, isPending, activeAsk])

  return (
    <div className={cn('flex flex-col', gap)} role="log" aria-live="polite" aria-label="Chat messages">
      {history
        .slice()
        .reverse()
        .map((turn) => (
          <div key={turn.id} className="animate-slide-up">
            <div className="flex justify-end mb-2.5">
              <p className="max-w-[85%] rounded-2xl rounded-br-md bg-gradient-to-br from-[#667eea] to-[#764ba2] px-4 py-2.5 text-body-sm font-medium text-white shadow-card">
                {turn.question}
              </p>
            </div>
            <div
              className={cn(
                'rounded-2xl rounded-bl-md border px-5 py-4 shadow-card',
                turn.answered ? 'bg-white border-campus-900/[0.07]' : 'bg-amber-50/80 border-amber-200/70',
              )}
            >
              <div className="flex items-center gap-2 mb-2">
                <span
                  className="flex h-6 w-6 items-center justify-center rounded-lg bg-gradient-to-br from-[#667eea] to-[#764ba2] flex-shrink-0"
                  aria-hidden="true"
                >
                  <Bot className="h-3.5 w-3.5 text-accent-300" />
                </span>
                <span className="text-micro font-semibold text-surface-400">
                  {turn.answered ? 'Campus AI' : 'Outside campus knowledge'} · {formatTime(turn.createdAt)}
                </span>
              </div>
              <CitedAnswer text={turn.answer ?? ''} idPrefix={`turn-${turn.id}`} />
              <EntityCards cards={turn.cards} title="Suggested matches" />
            </div>
          </div>
        ))}

      {(activeAsk || isPending) && activeAsk && (
        <div className="animate-slide-up">
          <div className="flex justify-end mb-2.5">
            <p className="max-w-[85%] rounded-2xl rounded-br-md bg-gradient-to-br from-[#667eea] to-[#764ba2] px-4 py-2.5 text-body-sm font-medium text-white shadow-card">
              {activeAsk}
            </p>
          </div>
          <div className="rounded-2xl rounded-bl-md border border-dashed border-campus-900/15 bg-white/70 px-5 py-4">
            <p className="flex items-center gap-2.5 text-body-sm text-surface-500">
              <Loader2 className="h-4 w-4 animate-spin text-campus-500" aria-hidden="true" />
              Searching campus knowledge…
            </p>
          </div>
        </div>
      )}

      {error && (
        <div
          className="rounded-2xl border border-red-200 bg-red-50/70 px-5 py-4 flex items-start gap-3 animate-slide-up"
          role="alert"
        >
          <AlertTriangle className="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" aria-hidden="true" />
          <div>
            <p className="font-semibold text-body-sm text-red-900">Couldn't reach the backend</p>
            <p className="text-body-sm text-red-700/80 mt-0.5">{error.message}</p>
          </div>
        </div>
      )}

      {liveResponse && !isPending && (
        <div className="animate-slide-up" key="live-answer">
          <div className="rounded-2xl rounded-bl-md border px-5 py-4 shadow-card bg-white border-campus-900/[0.07]">
            <div className="flex items-center gap-2 mb-2">
              <span
                className="flex h-6 w-6 items-center justify-center rounded-lg bg-gradient-to-br from-[#667eeb] to-[#764ba2] flex-shrink-0"
                aria-hidden="true"
              >
                <Bot className="h-3.5 w-3.5 text-accent-300" />
              </span>
              <span className="text-micro font-semibold text-surface-400">Campus AI · just now</span>
            </div>
            <CitedAnswer
              text={liveResponse.text ?? ''}
              sources={liveResponse.data?.sources as ChatSource[] | undefined}
              idPrefix="live-answer"
            />
            <Sources
              sources={liveResponse.data?.sources as ChatSource[] | undefined}
              idPrefix="live-answer"
            />
            <EntityCards cards={liveResponse.cards} title="Suggested matches" />
          </div>
        </div>
      )}

      <div ref={endRef} />
    </div>
  )
}
