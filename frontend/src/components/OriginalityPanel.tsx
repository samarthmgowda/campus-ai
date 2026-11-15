import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { TabRow } from '../components/ui/TabRow'
import {
  ScanSearch,
  ShieldCheck,
  ShieldAlert,
  FileWarning,
  Info,
  Library,
  Users,
  Database,
} from 'lucide-react'
import { cn } from '../utils/cn'

/* ——— Mirrors PlagiarismDetectionService.ScanResult (library corpus) ——— */

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

/* ——— Mirrors PlagiarismService.CrossScanResult (other submissions) ——— */

interface SubmissionMatch {
  submissionId: number
  title: string
  userId: string | null
  status: string | null
  score: number
  wordCoverage: number
  phraseCoverage: number
  /** Raw embedding cosine, or null when either side has no vector. */
  semanticScore: number | null
}

interface CrossScanResult {
  verdict: 'NONE' | 'LOW' | 'MODERATE' | 'HIGH' | 'INSUFFICIENT'
  topScore: number
  scannedSubmissions: number
  submissionWords: number
  /** How many pairs had embeddings on both sides. Zero means word-only. */
  semanticComparisons: number
  summary: string
  matches: SubmissionMatch[]
}

/** Mirrors SubmissionController.SubmissionSummary. */
interface StoredSubmission {
  id: number
  title: string
  userId: string | null
  status: string | null
  words: number
  excerpt: string
}

const VERDICT = {
  HIGH: { tone: 'danger', label: 'Close match', Icon: ShieldAlert },
  MODERATE: { tone: 'warning', label: 'Substantial overlap', Icon: FileWarning },
  LOW: { tone: 'info', label: 'Shared vocabulary', Icon: Info },
  NONE: { tone: 'success', label: 'No overlap found', Icon: ShieldCheck },
  INSUFFICIENT: { tone: 'default', label: 'Not enough text', Icon: Info },
} as const

const pct = (v: number) => `${Math.round(v * 100)}%`

type Scope = 'library' | 'submissions'

const SCOPES = [
  { id: 'library' as const, label: 'Library documents', icon: <Library className="h-3.5 w-3.5" aria-hidden="true" /> },
  { id: 'submissions' as const, label: 'Other submissions', icon: <Users className="h-3.5 w-3.5" aria-hidden="true" /> },
]

/**
 * Originality check, against two different corpora.
 *
 * The two checks answer different questions and neither substitutes for the
 * other. "Library documents" asks whether a submission lifted text from the
 * curated knowledge base. "Other submissions" asks whether it overlaps with
 * another student's work — the case the library check is structurally blind to,
 * because neither student's text is in the library at all, so both would come
 * back clean against it while being word-for-word copies of each other.
 *
 * Both scores are containment measures (see PlagiarismDetectionService), not a
 * percentage of "plagiarism". The copy says so, because a number this precise
 * invites a conclusion the method cannot support — it points a human at two
 * documents to read side by side, and nothing more.
 */
export function OriginalityPanel() {
  const [scope, setScope] = useState<Scope>('library')
  const [text, setText] = useState('')
  const [title, setTitle] = useState('')
  const queryClient = useQueryClient()

  const libraryScan = useMutation({
    mutationFn: (body: string) =>
      api.post<ScanResult>('/evaluation/plagiarism/scan', { text: body }),
  })

  const crossScan = useMutation({
    mutationFn: (body: string) =>
      api.post<CrossScanResult>('/evaluation/plagiarism/scan-all-submissions', { text: body }),
  })

  const corpus = useQuery({
    queryKey: ['submissions', 'corpus'],
    queryFn: () => api.get<StoredSubmission[]>('/submissions'),
    // Only the submissions scope actually reads the corpus, but it is a small
    // list and fetching it up front means the count is already there the first
    // time the scope is opened rather than flashing empty.
    staleTime: 30_000,
  })

  const store = useMutation({
    mutationFn: (body: { title: string; text: string }) =>
      // `embedded` says whether the stored copy also got a semantic vector.
      // It changes what future checks can find, so the response reports it.
      api.post<{ id: number; embedded: boolean }>('/submissions', body),
    onSuccess: () => {
      setTitle('')
      queryClient.invalidateQueries({ queryKey: ['submissions', 'corpus'] })
      // A stored submission changes what the cross-check compares against, so
      // a result from before the store is no longer about the same corpus.
      crossScan.reset()
    },
  })

  const active = scope === 'library' ? libraryScan : crossScan
  const result = scope === 'library' ? libraryScan.data : crossScan.data
  const verdict = result ? VERDICT[result.verdict] : null

  const chars = text.length
  const overLimit = chars > 50_000
  const canRun = !!text.trim() && !overLimit && !active.isPending
  // The text may have been edited since the check ran — showing the old score
  // against new text would be a quiet lie, so say so instead.
  const textChangedSinceScan =
    !!result && typeof active.variables === 'string' && active.variables !== text

  const stored = corpus.data?.length ?? 0

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
            Paste a submission once, then compare it against the library or against every other
            stored submission. The library check never leaves this server; the submission check
            also compares meaning, which needs the model server when it is running.
          </p>
        </div>
      </div>

      <TabRow
        tabs={SCOPES}
        value={scope}
        onChange={setScope}
        size="sm"
        ariaLabel="Compare against"
        className="mt-4"
      />

      <p className="text-caption text-ink-500 mt-2 min-h-[1.25rem]">
        {scope === 'library'
          ? 'Checks the text against every chunk of the curated knowledge base.'
          : `Checks the text against the ${stored} submission${stored === 1 ? '' : 's'} stored on this campus — the only way to catch two students copying from each other.`}
      </p>

      <div className="mt-3">
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

      <div className="flex flex-wrap items-center gap-2 mt-3">
        <Button
          size="sm"
          variant="accent"
          disabled={!canRun}
          loading={active.isPending}
          onClick={() => active.mutate(text)}
        >
          {scope === 'library' ? 'Check against library' : 'Check against all submissions'}
        </Button>
        {result && (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setText('')
              libraryScan.reset()
              crossScan.reset()
            }}
          >
            Clear
          </Button>
        )}
      </div>

      {active.isError && (
        <p className="text-body-sm text-red-300 mt-3" role="alert">
          {(active.error as Error).message}
        </p>
      )}

      {result && verdict && (
        <div className="mt-4 pt-4 border-t border-white/10" role="status" aria-live="polite">
          <div className="flex items-center gap-2 flex-wrap">
            <Badge variant={verdict.tone}>
              <verdict.Icon className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
              {verdict.label}
            </Badge>
            {result.verdict !== 'INSUFFICIENT' && (
              <span className="text-micro text-ink-400 tabular-nums">
                best match {pct(result.topScore)} · {result.submissionWords} words compared{' '}
                {scope === 'library'
                  ? `against ${(result as ScanResult).scannedChunks} library chunks`
                  : `against ${(result as CrossScanResult).scannedSubmissions} other submission${
                      (result as CrossScanResult).scannedSubmissions === 1 ? '' : 's'
                    }`}
              </span>
            )}
          </div>
          <p className="text-body-sm text-ink-300 mt-2 leading-relaxed">{result.summary}</p>

          {textChangedSinceScan && (
            <p className="text-caption text-amber-300 mt-2">
              The text was edited after this check ran — run it again for a result that matches.
            </p>
          )}

          {scope === 'library' && (result as ScanResult).matches.length > 0 && (
            <ul className="mt-3 space-y-2">
              {(result as ScanResult).matches.map(m => (
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
                  <ScoreBar score={m.score} />
                </li>
              ))}
            </ul>
          )}

          {scope === 'submissions' && (result as CrossScanResult).matches.length > 0 && (
            <ul className="mt-3 space-y-2">
              {(result as CrossScanResult).matches.map(m => (
                <li
                  key={m.submissionId}
                  className="rounded-xl border border-white/10 bg-er-900 px-3.5 py-2.5"
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="font-medium text-body-sm text-ink-100 truncate">{m.title}</p>
                    <p className="text-body-sm font-bold tabular-nums text-ink-50 flex-shrink-0">
                      {pct(m.score)}
                    </p>
                  </div>
                  <p className="text-micro text-ink-400 mt-0.5 tabular-nums">
                    {m.userId ?? 'unattributed'}
                    {m.status && <> · {m.status.toLowerCase()}</>}
                    {' · '}
                    {pct(m.wordCoverage)} of words · {pct(m.phraseCoverage)} of phrases
                    {m.semanticScore != null && <> · {pct(m.semanticScore)} of meaning</>}
                  </p>
                  <ScoreBar score={m.score} />
                </li>
              ))}
            </ul>
          )}

          {scope === 'submissions' && (
            <p className="text-caption text-ink-400 mt-3">
              {(() => {
                const cross = result as CrossScanResult
                if (cross.semanticComparisons === 0) {
                  return 'Meaning was not compared on this run — the model server was unreachable, so this result covers wording only. A reworded copy could be missing from it.'
                }
                const total = cross.scannedSubmissions
                // Partial coverage is the quiet case: some rows have vectors and
                // some do not, so neither "we checked meaning" nor "we did not"
                // is true of the whole result.
                if (cross.semanticComparisons < total) {
                  return `Meaning was compared for ${cross.semanticComparisons} of ${total} submissions; the rest predate the embedding column, so a reworded copy of one of those would be missing.`
                }
                return `Meaning was compared as well as wording, across all ${total} submission${total === 1 ? '' : 's'}.`
              })()}
            </p>
          )}

          <p className="text-micro text-ink-500 mt-3">
            {scope === 'library'
              ? 'Shared vocabulary is not proof of copying. Treat this as a pointer to the document worth reading side by side.'
              : 'A high score is a pair of submissions to read side by side, not a verdict against a student.'}
          </p>
        </div>
      )}

      {scope === 'submissions' && (
        <div className="mt-4 pt-4 border-t border-white/10">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="flex items-center gap-1.5 text-caption text-ink-400">
              <Database className="h-3.5 w-3.5 text-ink-500" aria-hidden="true" />
              {corpus.isLoading
                ? 'Reading the submission corpus…'
                : corpus.isError
                  ? 'Could not read the submission corpus.'
                  : `${stored} submission${stored === 1 ? '' : 's'} stored`}
            </p>
          </div>

          {!corpus.isLoading && !corpus.isError && (
            <p className="text-micro text-ink-500 mt-1">
              {stored === 0
                ? 'Nothing is stored yet, so there is nothing to compare against. Store the text below to start the corpus.'
                : stored === 1
                  ? 'Comparing submissions needs at least two, so store one more before the check can find anything.'
                  : 'Storing a submission adds it to the corpus every future check compares against.'}
            </p>
          )}

          <div className="mt-2.5 flex flex-wrap items-end gap-2">
            <div className="min-w-0 flex-1">
              <label htmlFor="plagiarism-store-title" className="block text-micro text-ink-400 mb-1">
                Title for the stored copy
              </label>
              <input
                id="plagiarism-store-title"
                value={title}
                onChange={e => setTitle(e.target.value)}
                placeholder="e.g. Data Structures — final report"
                className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-1.5 text-body-sm text-ink-100 placeholder:text-ink-500 focus:outline-none focus:ring-2 focus:ring-er-500"
              />
            </div>
            <Button
              size="sm"
              variant="secondary"
              disabled={!text.trim() || overLimit || store.isPending}
              loading={store.isPending}
              onClick={() => store.mutate({ title: title.trim(), text })}
            >
              Store this text
            </Button>
          </div>

          {store.isError && (
            <p className="text-body-sm text-red-300 mt-2" role="alert">
              {(store.error as Error).message}
            </p>
          )}
          {store.isSuccess && (
            <p
              className={cn(
                'text-caption mt-2',
                store.data.embedded ? 'text-emerald-300' : 'text-amber-300',
              )}
              role="status"
            >
              {store.data.embedded
                ? 'Stored, with a semantic fingerprint. Future checks compare it on meaning as well as wording.'
                : 'Stored, but without a semantic fingerprint — the model server was unreachable. It still counts for word overlap; a reworded copy of it would be missed.'}
            </p>
          )}
        </div>
      )}
    </section>
  )
}

/**
 * A second reading of the same score — a bar to scan down a list by, not new
 * information. Kept local because both match shapes render it identically.
 */
function ScoreBar({ score }: { score: number }) {
  return (
    <div className="mt-2 h-1 rounded-full bg-white/[0.06] overflow-hidden" aria-hidden="true">
      <div
        className="h-full rounded-full bg-er-500"
        style={{ width: `${Math.max(2, Math.round(score * 100))}%` }}
      />
    </div>
  )
}
