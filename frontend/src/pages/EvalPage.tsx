import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState } from '../components/ui/PageState'
import { FlaskConical, Users, Trophy, Play, ChevronDown } from 'lucide-react'
import { cn } from '../utils/cn'

/* ——— Types mirroring RagEvalService / TeammateEvalService records ——— */

interface ArmResult {
  arm: string
  recalled: boolean
  topTitles: string[]
  answered: boolean
  correct: boolean
  faithful: boolean
  cited: boolean
  latencyMs: number | null
  answerPreview: string | null
}

interface RagCase {
  question: string
  expectedDocument: string | null
  answerable: boolean
  basic: ArmResult
  improved: ArmResult
}

interface RagSummary {
  totalCases: number
  answerable: number
  unanswerable: number
  recallAtKBasic: number
  recallAtKImproved: number
  correctnessBasic: number
  correctnessImproved: number
  faithfulnessBasic: number
  faithfulnessImproved: number
  citationRateBasic: number
  citationRateImproved: number
  precisionAtKBasic: number
  precisionAtKImproved: number
  abstentionRate: number
  avgLatencyBasicMs: number
  avgLatencyImprovedMs: number
  improvedWins: number
  ties: number
  basicWins: number
  verdict: { winner: string; reason: string }
  cases: RagCase[]
}

interface TeammateArm {
  arm: string
  cases: number
  hitAt3: number
  hitAt5: number
  meanRank: number
  avgRatingTop3: number
  ratingCoverageTop3: number
  semanticUsed: boolean
}

interface TeammateCase {
  projectName: string
  heldOutStudent: string
  gaps: string[]
  rankOverlap: number
  rankHybrid: number
  rankSemantic: number
}

interface TeammateSummary {
  projects: number
  cases: number
  skippedFiltered: number
  arms: TeammateArm[]
  verdict: { winner: string; reason: string }
  detail: TeammateCase[]
}

function VerdictBanner({ verdict }: { verdict: { winner: string; reason: string } }) {
  const tone =
    verdict.winner === 'improved' || verdict.winner === 'hybrid' || verdict.winner === 'hybrid+semantic'
      ? 'from-[#667eea] to-[#764ba2] text-white border-transparent'
      : verdict.winner === 'tie'
        ? 'bg-amber-50 border-amber-200 text-amber-900'
        : 'bg-white border-campus-900/[0.07] text-campus-950'
  return (
    <div className={cn('rounded-2xl border px-4 py-3.5 shadow-card bg-gradient-to-br flex items-start gap-3', tone)}>
      <Trophy className="h-5 w-5 flex-shrink-0 mt-0.5" aria-hidden="true" />
      <div>
        <p className="font-display font-semibold text-[15px]">
          Verdict: <span className="font-bold">{verdict.winner}</span>
        </p>
        <p className={cn('text-body-sm mt-0.5', verdict.winner.startsWith('hybrid') || verdict.winner === 'improved' ? 'text-white/80' : 'opacity-80')}>
          {verdict.reason}
        </p>
      </div>
    </div>
  )
}

function MetricRow({ label, basic, improved, fmt }: { label: string; basic: number; improved: number; fmt?: (v: number) => string }) {
  const f = fmt ?? ((v: number) => v.toFixed(3))
  const better = improved > basic ? 'imp' : basic > improved ? 'bas' : 'tie'
  return (
    <tr className="border-t border-campus-900/[0.06]">
      <td className="py-2 pr-3 text-body-sm text-surface-600">{label}</td>
      <td className={cn('py-2 pr-3 text-body-sm tabular-nums text-right', better === 'bas' && 'font-bold text-campus-700')}>{f(basic)}</td>
      <td className={cn('py-2 text-body-sm tabular-nums text-right', better === 'imp' && 'font-bold text-campus-700')}>{f(improved)}</td>
    </tr>
  )
}

function BoolDot({ v, invert }: { v: boolean; invert?: boolean }) {
  const good = invert ? !v : v
  return (
    <span
      className={cn(
        'inline-block h-2.5 w-2.5 rounded-full',
        good ? 'bg-emerald-500' : v && invert ? 'bg-red-500' : 'bg-surface-300'
      )}
      title={String(v)}
      aria-label={String(v)}
    />
  )
}

function rankCell(r: number) {
  if (r < 0) return <span className="text-surface-300">miss</span>
  return (
    <span className={cn('tabular-nums font-medium', r <= 3 ? 'text-emerald-700' : r <= 5 ? 'text-amber-700' : 'text-surface-500')}>
      #{r}
    </span>
  )
}

export default function EvalPage() {
  const [retrievalOnly, setRetrievalOnly] = useState(true)
  const [availableOnly, setAvailableOnly] = useState(true)
  const [includeSemantic, setIncludeSemantic] = useState(true)
  const [showRagCases, setShowRagCases] = useState(false)
  const [showTmCases, setShowTmCases] = useState(false)
  const [ragError, setRagError] = useState<string | null>(null)
  const [tmError, setTmError] = useState<string | null>(null)

  const rag = useMutation({
    mutationFn: () => api.post<RagSummary>(`/eval/rag/run?retrievalOnly=${retrievalOnly}`, {}),
    onSuccess: () => setRagError(null),
    onError: (e: Error) => setRagError(e.message),
  })
  const tm = useMutation({
    mutationFn: () => api.post<TeammateSummary>(`/eval/teammates/run?availableOnly=${availableOnly}&includeSemantic=${includeSemantic}`, {}),
    onSuccess: () => setTmError(null),
    onError: (e: Error) => setTmError(e.message),
  })

  return (
    <div className="mx-auto max-w-5xl space-y-8">
      <PageHeader
        title="Evaluation"
        sub="Research harness: RAG retrieval vs hybrid+rerank, and teammate arms vs overlap baseline. Admin only."
        actions={<Badge variant="outline">admin</Badge>}
      />

      {/* ——— RAG eval ——— */}
      <section aria-label="RAG evaluation" className="rounded-2xl border border-campus-900/[0.06] bg-white p-5 shadow-card">
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-campus-50 border border-campus-100">
              <FlaskConical className="h-5 w-5 text-campus-600" aria-hidden="true" />
            </span>
            <div>
              <h3 className="font-display font-semibold text-[17px] text-campus-950">RAG harness</h3>
              <p className="text-caption text-surface-500">9 curated cases · basic (fusion) vs improved (hybrid + rerank)</p>
            </div>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <label className="flex items-center gap-2 text-body-sm text-surface-600 cursor-pointer">
              <input
                type="checkbox"
                checked={retrievalOnly}
                onChange={e => setRetrievalOnly(e.target.checked)}
                className="h-4 w-4 accent-[#764ba2]"
              />
              Retrieval only
              <span className="text-caption text-surface-400">(uncheck for full generation — slower, needs Ollama)</span>
            </label>
            <Button size="sm" loading={rag.isPending} onClick={() => rag.mutate()}>
              <Play className="h-4 w-4" aria-hidden="true" />
              Run RAG eval
            </Button>
          </div>
        </div>

        {ragError && <p role="alert"><Badge variant="danger">{ragError}</Badge></p>}

        {rag.data ? (
          <div className="space-y-4">
            <VerdictBanner verdict={rag.data.verdict} />
            <div className="flex gap-2 flex-wrap">
              <Badge variant="info">{rag.data.totalCases} cases</Badge>
              <Badge variant="success">improved wins {rag.data.improvedWins}</Badge>
              <Badge variant="default">ties {rag.data.ties}</Badge>
              <Badge variant="warning">basic wins {rag.data.basicWins}</Badge>
              {!retrievalOnly && <Badge variant="outline">abstention {rag.data.abstentionRate.toFixed(2)}</Badge>}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="text-micro font-semibold text-surface-400">
                    <th className="pb-1 pr-3 font-semibold">Metric</th>
                    <th className="pb-1 pr-3 font-semibold text-right">Basic</th>
                    <th className="pb-1 font-semibold text-right">Improved</th>
                  </tr>
                </thead>
                <tbody>
                  <MetricRow label="Recall@K" basic={rag.data.recallAtKBasic} improved={rag.data.recallAtKImproved} />
                  <MetricRow label="Precision@K" basic={rag.data.precisionAtKBasic} improved={rag.data.precisionAtKImproved} />
                  {!retrievalOnly && (
                    <>
                      <MetricRow label="Correctness" basic={rag.data.correctnessBasic} improved={rag.data.correctnessImproved} />
                      <MetricRow label="Faithfulness" basic={rag.data.faithfulnessBasic} improved={rag.data.faithfulnessImproved} />
                      <MetricRow label="Citation rate" basic={rag.data.citationRateBasic} improved={rag.data.citationRateImproved} />
                      <MetricRow label="Avg latency (ms)" basic={rag.data.avgLatencyBasicMs} improved={rag.data.avgLatencyImprovedMs} fmt={v => Math.round(v).toString()} />
                    </>
                  )}
                </tbody>
              </table>
            </div>
            <button
              className="flex items-center gap-1.5 text-body-sm font-medium text-campus-600 hover:text-campus-700"
              onClick={() => setShowRagCases(!showRagCases)}
              aria-expanded={showRagCases}
            >
              <ChevronDown className={cn('h-4 w-4 transition-transform', showRagCases && 'rotate-180')} aria-hidden="true" />
              {showRagCases ? 'Hide' : 'Show'} {rag.data.cases.length} case details
            </button>
            {showRagCases && (
              <div className="space-y-2.5">
                {rag.data.cases.map((c, i) => (
                  <div key={i} className="rounded-xl border border-campus-900/[0.07] bg-surface-100/50 p-3.5">
                    <p className="text-body-sm font-medium text-campus-950">{c.question}</p>
                    <p className="text-caption text-surface-500 mt-0.5">
                      {c.answerable ? `expects ${c.expectedDocument ?? '—'}` : 'unanswerable (abstain expected)'}
                    </p>
                    <div className="grid sm:grid-cols-2 gap-2 mt-2.5">
                      {([['basic', c.basic], ['improved', c.improved]] as const).map(([arm, r]) => (
                        <div key={arm} className="rounded-lg bg-white border border-campus-900/[0.06] p-2.5">
                          <p className="text-micro font-semibold text-surface-400 uppercase tracking-wide">{arm}</p>
                          <div className="flex items-center gap-2.5 mt-1.5 text-caption text-surface-600">
                            <span className="flex items-center gap-1"><BoolDot v={r.recalled} /> recalled</span>
                            {!retrievalOnly && (
                              <>
                                <span className="flex items-center gap-1"><BoolDot v={r.correct} /> correct</span>
                                <span className="flex items-center gap-1"><BoolDot v={r.faithful} /> faithful</span>
                                <span className="flex items-center gap-1"><BoolDot v={r.cited} /> cited</span>
                              </>
                            )}
                          </div>
                          <p className="text-caption text-surface-500 mt-1.5 truncate" title={r.topTitles.join(' · ')}>
                            {r.topTitles.join(' · ') || 'no hits'}
                          </p>
                          {r.answerPreview && <p className="text-caption text-surface-600 mt-1 line-clamp-2">{r.answerPreview}</p>}
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          <EmptyState
            icon={FlaskConical}
            title="No RAG run yet"
            hint="Run the harness to compare retrieval arms on the curated Q/A set. Retrieval-only is fast; full mode scores generation too."
          />
        )}
      </section>

      {/* ——— Teammate eval ——— */}
      <section aria-label="Teammate evaluation" className="rounded-2xl border border-campus-900/[0.06] bg-white p-5 shadow-card">
        <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-campus-50 border border-campus-100">
              <Users className="h-5 w-5 text-campus-600" aria-hidden="true" />
            </span>
            <div>
              <h3 className="font-display font-semibold text-[17px] text-campus-950">Teammate arms</h3>
              <p className="text-caption text-surface-500">leave-one-out · overlap vs hybrid vs hybrid+semantic</p>
            </div>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <label className="flex items-center gap-2 text-body-sm text-surface-600 cursor-pointer">
              <input type="checkbox" checked={availableOnly} onChange={e => setAvailableOnly(e.target.checked)} className="h-4 w-4 accent-[#764ba2]" />
              Available only
            </label>
            <label className="flex items-center gap-2 text-body-sm text-surface-600 cursor-pointer">
              <input type="checkbox" checked={includeSemantic} onChange={e => setIncludeSemantic(e.target.checked)} className="h-4 w-4 accent-[#764ba2]" />
              +semantic arm
              <span className="text-caption text-surface-400">(needs Ollama)</span>
            </label>
            <Button size="sm" loading={tm.isPending} onClick={() => tm.mutate()}>
              <Play className="h-4 w-4" aria-hidden="true" />
              Run teammate eval
            </Button>
          </div>
        </div>

        {tmError && <p role="alert"><Badge variant="danger">{tmError}</Badge></p>}

        {tm.data ? (
          <div className="space-y-4">
            <VerdictBanner verdict={tm.data.verdict} />
            <div className="flex gap-2 flex-wrap">
              <Badge variant="info">{tm.data.projects} projects</Badge>
              <Badge variant="info">{tm.data.cases} cases</Badge>
              <Badge variant="outline">{tm.data.skippedFiltered} skipped (filtered)</Badge>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="text-micro font-semibold text-surface-400">
                    <th className="pb-1 pr-3 font-semibold">Arm</th>
                    <th className="pb-1 pr-3 font-semibold text-right">hit@3</th>
                    <th className="pb-1 pr-3 font-semibold text-right">hit@5</th>
                    <th className="pb-1 pr-3 font-semibold text-right">Mean rank</th>
                    <th className="pb-1 pr-3 font-semibold text-right">Avg rating</th>
                    <th className="pb-1 font-semibold text-right">Coverage</th>
                  </tr>
                </thead>
                <tbody>
                  {tm.data.arms.map(a => (
                    <tr key={a.arm} className="border-t border-campus-900/[0.06]">
                      <td className="py-2 pr-3 text-body-sm font-medium text-campus-950">
                        {a.arm}
                        {a.semanticUsed && <Badge variant="accent" size="sm" className="ml-2">semantic live</Badge>}
                      </td>
                      <td className="py-2 pr-3 text-body-sm tabular-nums text-right">{a.hitAt3.toFixed(3)}</td>
                      <td className="py-2 pr-3 text-body-sm tabular-nums text-right">{a.hitAt5.toFixed(3)}</td>
                      <td className="py-2 pr-3 text-body-sm tabular-nums text-right">{a.meanRank.toFixed(2)}</td>
                      <td className="py-2 pr-3 text-body-sm tabular-nums text-right">{a.avgRatingTop3.toFixed(2)}</td>
                      <td className="py-2 text-body-sm tabular-nums text-right">{a.ratingCoverageTop3.toFixed(3)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <button
              className="flex items-center gap-1.5 text-body-sm font-medium text-campus-600 hover:text-campus-700"
              onClick={() => setShowTmCases(!showTmCases)}
              aria-expanded={showTmCases}
            >
              <ChevronDown className={cn('h-4 w-4 transition-transform', showTmCases && 'rotate-180')} aria-hidden="true" />
              {showTmCases ? 'Hide' : 'Show'} {tm.data.detail.length} leave-one-out cases
            </button>
            {showTmCases && (
              <div className="overflow-x-auto">
                <table className="w-full text-left">
                  <thead>
                    <tr className="text-micro font-semibold text-surface-400">
                      <th className="pb-1 pr-3 font-semibold">Project · held-out</th>
                      <th className="pb-1 pr-3 font-semibold">Gaps</th>
                      <th className="pb-1 pr-3 font-semibold text-right">Overlap</th>
                      <th className="pb-1 pr-3 font-semibold text-right">Hybrid</th>
                      <th className="pb-1 font-semibold text-right">Semantic</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tm.data.detail.map((d, i) => (
                      <tr key={i} className="border-t border-campus-900/[0.06]">
                        <td className="py-2 pr-3 text-body-sm text-campus-950">
                          <span className="font-medium">{d.projectName}</span>
                          <span className="text-surface-500"> · {d.heldOutStudent}</span>
                        </td>
                        <td className="py-2 pr-3 text-caption text-surface-500">{d.gaps.join(', ')}</td>
                        <td className="py-2 pr-3 text-body-sm text-right">{rankCell(d.rankOverlap)}</td>
                        <td className="py-2 pr-3 text-body-sm text-right">{rankCell(d.rankHybrid)}</td>
                        <td className="py-2 text-body-sm text-right">{d.rankSemantic < 0 && !includeSemantic ? <span className="text-surface-300">—</span> : rankCell(d.rankSemantic)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        ) : (
          <EmptyState
            icon={Users}
            title="No teammate run yet"
            hint="Run the leave-one-out comparison across active multi-member projects to see which arm recovers held-out members best."
          />
        )}
      </section>
    </div>
  )
}
