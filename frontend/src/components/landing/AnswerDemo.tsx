import { useEffect, useState } from 'react'
import { cn } from '../../utils/cn'
import { useReducedMotion } from '../../hooks/useReducedMotion'
import { FileText, Check } from 'lucide-react'

/** The demo's script. Typed out one character at a time, then answered. */
const QUESTION = 'How many students can be in a hackathon team?'

const CITED_LINE =
  'A hackathon team may have between 2 and 4 members. Teams larger than 4 are not permitted without faculty approval.'

const SOURCES = [
  { title: 'Hackathon Rules', page: 'p. 3' },
  { title: 'Student Activities Handbook', page: 'p. 12' },
]

type Phase = 'idle' | 'typing' | 'thinking' | 'answering' | 'done'

/**
 * The hero's centrepiece: the product answering a real campus question, with
 * the citation that grounds it.
 *
 * This is the one orchestrated moment on the page. Everything else stays quiet
 * on purpose — a landing page that animates every section reads as generated,
 * and the point here is to show the actual differentiator (grounded answers,
 * not invented ones) instead of decorating a headline.
 */
export default function AnswerDemo({ start = true }: { start?: boolean }) {
  const reduced = useReducedMotion()
  const [phase, setPhase] = useState<Phase>(reduced ? 'done' : 'idle')
  const [typed, setTyped] = useState(reduced ? QUESTION : '')

  useEffect(() => {
    if (reduced) {
      setTyped(QUESTION)
      setPhase('done')
      return
    }
    if (!start) return

    let cancelled = false
    const timers: number[] = []
    const wait = (ms: number) =>
      new Promise<void>((resolve) => {
        const t = window.setTimeout(resolve, ms)
        timers.push(t)
      })

    const run = async () => {
      setPhase('idle')
      setTyped('')
      await wait(520)
      if (cancelled) return

      setPhase('typing')
      for (let i = 1; i <= QUESTION.length; i++) {
        if (cancelled) return
        setTyped(QUESTION.slice(0, i))
        await wait(QUESTION[i - 1] === ' ' ? 28 : 22)
      }

      await wait(280)
      if (cancelled) return
      setPhase('thinking')
      await wait(950)
      if (cancelled) return

      setPhase('answering')
      await wait(420)
      if (cancelled) return
      setPhase('done')
    }

    void run()
    return () => {
      cancelled = true
      timers.forEach((t) => window.clearTimeout(t))
    }
  }, [reduced, start])

  const answerVisible = phase === 'answering' || phase === 'done'

  return (
    <div className="relative w-full max-w-[560px]">
      {/* The card: a plain sheet of paper with real content on it. */}
      <div className="relative rounded-2xl border border-campus-900/[0.08] bg-white shadow-[0_24px_70px_-24px_rgba(46,42,92,0.35)] overflow-hidden">
        {/* Window chrome, kept minimal: three dots and a title. */}
        <div className="flex items-center gap-2 border-b border-campus-900/[0.06] bg-surface-50/80 px-4 py-2.5">
          <span className="flex gap-1.5" aria-hidden="true">
            <span className="h-2.5 w-2.5 rounded-full bg-[#ff5f57]" />
            <span className="h-2.5 w-2.5 rounded-full bg-[#febc2e]" />
            <span className="h-2.5 w-2.5 rounded-full bg-[#28c840]" />
          </span>
          <p className="ml-1 text-micro font-medium text-surface-500">Ask Campus AI</p>
        </div>

        <div className="px-5 py-5">
          {/* The question, typed live. */}
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-surface-100 text-micro font-bold text-surface-600" aria-hidden="true">
              You
            </span>
            <p className="pt-1 text-body-sm font-medium text-campus-950 min-h-[1.6em]">
              {/* The whole question is exposed to assistive tech; the visible
                  text is what's being typed. */}
              <span className="sr-only">{QUESTION}</span>
              <span aria-hidden="true">{typed}</span>
              <span
                aria-hidden="true"
                className={cn(
                  'inline-block w-[2px] h-[1.05em] translate-y-[2px] bg-campus-500 ml-0.5',
                  phase === 'typing' ? 'animate-caret' : 'opacity-0'
                )}
              />
            </p>
          </div>

          {/* Thinking indicator */}
          <div
            className={cn(
              'mt-4 flex items-center gap-2 pl-10 transition-opacity duration-300',
              phase === 'thinking' ? 'opacity-100' : 'opacity-0'
            )}
            aria-hidden={phase !== 'thinking'}
          >
            <span className="text-caption text-surface-500">Searching campus documents</span>
            <span className="flex gap-1" aria-hidden="true">
              {[0, 1, 2].map((i) => (
                <span
                  key={i}
                  className="h-1.5 w-1.5 rounded-full bg-campus-400 animate-pulse-soft"
                  style={{ animationDelay: `${i * 160}ms` }}
                />
              ))}
            </span>
          </div>

          {/* The grounded answer. */}
          <div
            className={cn(
              'mt-4 transition-all duration-500 ease-out-expo',
              answerVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2 pointer-events-none'
            )}
          >
            <div className="flex items-start gap-3">
              <span className="mt-0.5 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-micro font-bold text-white" aria-hidden="true">
                AI
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-body-sm leading-relaxed text-surface-700">
                  {CITED_LINE}
                  <sup className="ml-0.5 font-semibold text-campus-600">[1][2]</sup>
                </p>

                <div className="mt-3.5 flex flex-wrap gap-2">
                  {SOURCES.map((s, i) => (
                    <span
                      key={s.title}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-campus-100 bg-campus-50/70 px-2.5 py-1.5 text-micro text-campus-800"
                    >
                      <FileText className="h-3.5 w-3.5 flex-shrink-0 text-campus-500" aria-hidden="true" />
                      <span className="font-medium">[{i + 1}]</span>
                      <span className="truncate max-w-[180px]">{s.title}</span>
                      <span className="text-campus-500">{s.page}</span>
                    </span>
                  ))}
                </div>
              </div>
            </div>

            {/* The grounding claim, earned rather than asserted. */}
            <p className="mt-4 flex items-center gap-1.5 pl-10 text-micro text-surface-400">
              <Check className="h-3.5 w-3.5 text-emerald-500" aria-hidden="true" />
              Answered only from documents in your campus library
            </p>
          </div>
        </div>
      </div>

      {/* A second sheet peeking out behind, to suggest there is more here. */}
      <div
        className="absolute -bottom-3 left-6 right-6 -z-10 h-12 rounded-2xl border border-campus-900/[0.06] bg-white/60"
        aria-hidden="true"
      />
      <div
        className="absolute -bottom-6 left-12 right-12 -z-20 h-12 rounded-2xl border border-campus-900/[0.04] bg-white/40"
        aria-hidden="true"
      />
    </div>
  )
}
