import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { ScanSearch, ShieldCheck, ShieldAlert, FileWarning, Info } from 'lucide-react'
import { cn } from '../utils/cn'

/* ——— Mirrors PlagiarismDetectionService.ScanResult ——— */

interface SourceMatch {
  documentId: number
  documentTitle: string
  section: string | null
  pageNumber: number | null
  score: number
  wordCoverage: number
  phraseCoverage: number
}

interface ScanResult {
  verdict: 'NONE' | 'LOW' | 'MODERATE' | 'HIGH' | 'INSUFFICIENT'
  topScore: number
  scannedChunks: number
  submissionWords: number
  summary: string
  matches: SourceMatch[]
}

const VERDICT = {
  HIGH: { tone: 'danger', label: 'Close match', Icon: ShieldAlert },
  MODERATE: { tone: 'warning', label: 'Substantial overlap', Icon: FileWarning },
  LOW: { tone: 'info', label: 'Shared vocabulary', Icon: Info },
  NONE: { tone: 'success', label: 'No overlap found', Icon: ShieldCheck },
  INSUFFICIENT: { tone: 'default', label: 'Not enough text', Icon: Info },
} as const

const pct = (v: number) => `${Math.round(v * 100)}%`

/**
 * Originality check against the Library corpus.
 *
 * The score is a containment measure (see PlagiarismDetectionService), not a
 * percentage of "plagiarism". The copy says so explicitly, because a number
 * this precise invites a conclusion the method cannot support — it points a
 * human at a document to compare, and nothing more.
 */
export function OriginalityPanel() {
  const [text, setText] = useState('')

  const scan = useMutation({
    mutationFn: (body: string) =>
      api.post<ScanResult>('/evaluation/plagiarism/scan', { text: body }),
  })

  const result = scan.data
  const verdict = result ? VERDICT[result.verdict] : null
  const chars = text.length
  const overLimit = chars > 50_000

  return (
    <section
      aria-label="Originality check"
      className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-5 mb-5"
    >
      <div className="flex items-start gap-3">
        <span
          className="flex h-10 w-10 items-center justify-center rounded-xl bg-er-900 border border-white/10 flex-shrink-0"
          aria-hidden="true"
        >
          <ScanSearch className="h-[18px] w-[18px] text-er-300" />
        </span>
        <div className="min-w-0">
          <h2 className="font-display font-semibold text-[15px] text-ink-50 tracking-tight">
            Originality check
          </h2>
          <p className="text-caption text-ink-400 mt-0.5">
            Paste a submission to see which library documents it overlaps with. Runs locally — no
            model server needed.
          </p>
        </div>
      </div>

      <div className="mt-4">
        <label htmlFor="plagiarism-text" className="block text-body-sm font-medium text-ink-200 mb-1.5">
          Submission text
        </label>
        <textarea
          id="plagiarism-text"
          value={text}
          onChange={e => setText(e.target.value)}
          rows={5}
          placeholder="Paste the student's submission here…"
          aria-describedby="plagiarism-hint"
          className="w-full rounded-xl border border-white/12 bg-er-900 px-3 py-2 text-body-sm text-ink-100 placeholder:text-ink-500 focus:outline-none focus:ring-2 focus:ring-er-500 resize-y"
        />
        <div className="flex items-center justify-between gap-3 mt-1.5">
          <p id="plagiarism-hint" className={cn('text-caption', overLimit ? 'text-red-300' : 'text-ink-400')}>
            {overLimit
              ? `${chars.toLocaleString()} characters — the limit is 50,000. Check a section at a time.`
              : 'At least 8 distinct words are needed for a meaningful comparison.'}
          </p>
          <p className={cn('text-micro tabular-nums', overLimit ? 'text-red-300' : 'text-ink-500')}>
            {chars.toLocaleString()} / 50,000
          </p>
        </div>
      </div>

      <div className="flex gap-2 mt-3">
        <Button
          size="sm"
          variant="accent"
          disabled={!text.trim() || overLimit || scan.isPending}
          loading={scan.isPending}
          onClick={() => scan.mutate(text)}
        >
          Check originality
        </Button>
        {result && (
          <Button size="sm" variant="ghost" onClick={() => { setText(''); scan.reset() }}>
            Clear
          </Button>
        )}
      </div>

      {scan.isError && (
        <p className="text-body-sm text-red-300 mt-3" role="alert">
          {(scan.error as Error).message}
        </p>
      )}

      {result && verdict && (
        <div className="mt-4 pt-4 border-t border-white/10" role="status" aria-live="polite">
          <div className="flex items-center gap-2 flex-wrap">
            <Badge variant={verdict.tone}>
              <verdict.Icon className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
              {verdict.label}
            </Badge>
            {result.verdict !== 'INSUFFICIENT' && result.scannedChunks > 0 && (
              <span className="text-micro text-ink-400 tabular-nums">
                best match {pct(result.topScore)} · {result.submissionWords} words compared against{' '}
                {result.scannedChunks} chunks
              </span>
            )}
          </div>
          <p className="text-body-sm text-ink-300 mt-2 leading-relaxed">{result.summary}</p>

          {result.matches.length > 0 && (
            <ul className="mt-3 space-y-2">
              {result.matches.map(m => (
                <li
                  key={m.documentId}
                  className="rounded-xl border border-white/10 bg-er-900 px-3.5 py-2.5"
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="font-medium text-body-sm text-ink-100 truncate">{m.documentTitle}</p>
                    <p className="text-body-sm font-bold tabular-nums text-ink-50 flex-shrink-0">
                      {pct(m.score)}
                    </p>
                  </div>
                  <p className="text-micro text-ink-400 mt-0.5 tabular-nums">
                    {m.section && <>{m.section} · </>}
                    {m.pageNumber != null && <>page {m.pageNumber} · </>}
                    {pct(m.wordCoverage)} of words · {pct(m.phraseCoverage)} of phrases
                  </p>
                  {/* The bar shows the same number the text does — it is a
                      second reading of the score for scanning, not new info. */}
                  <div className="mt-2 h-1 rounded-full bg-white/[0.06] overflow-hidden" aria-hidden="true">
                    <div
                      className="h-full rounded-full bg-er-500"
                      style={{ width: `${Math.max(2, Math.round(m.score * 100))}%` }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )}

          <p className="text-micro text-ink-500 mt-3">
            Shared vocabulary is not proof of copying. Treat this as a pointer to the document worth
            reading side by side.
          </p>
        </div>
      )}
    </section>
  )
}
