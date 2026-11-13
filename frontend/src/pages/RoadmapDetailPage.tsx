import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Building2, UserRound } from 'lucide-react'
import { api } from '../api/client'
import { cn } from '../utils/cn'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader } from '../components/ui/PageState'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/Card'
import { Button } from '../components/ui/Button'

/**
 * A single career roadmap, with prev/next navigation across the full list.
 *
 * Scoped to what the API actually returns (see RoadmapController#toMap):
 * title, targetRole, description and the publishing alumni's name, role and
 * company. There is no episodes/resources payload on the backend, so this page
 * does not invent one — the reading order is the roadmap itself.
 *
 * Route registration is still pending; the path is /roadmaps/:roadmapId.
 */

interface RoadmapDto {
  id: number
  title: string
  targetRole: string
  description: string
  alumniId?: number
  alumniName?: string | null
  alumniRole?: string | null
  alumniCompany?: string | null
}

export default function RoadmapDetailPage() {
  const { roadmapId } = useParams<{ roadmapId: string }>()
  const navigate = useNavigate()

  const [roadmaps, setRoadmaps] = useState<RoadmapDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    api
      .get<RoadmapDto[]>('/roadmaps')
      .then((list) => {
        if (cancelled) return
        // `/roadmaps` may hand back either an array or a paged envelope; treat
        // both as a plain list so a shape change cannot blank the page.
        const rows = Array.isArray(list) ? list : []
        setRoadmaps(rows)
        // A missing or unknown id falls back to the first roadmap rather than
        // rendering an empty page with no explanation.
        const known = rows.some((r) => String(r.id) === String(roadmapId))
        if (roadmapId !== undefined && !known && rows.length > 0) {
          navigate(`/roadmaps/${rows[0].id}`, { replace: true })
        }
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message || 'Could not load roadmaps.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [roadmapId, navigate])

  const index = roadmaps.findIndex((r) => String(r.id) === String(roadmapId))
  const current = index >= 0 ? roadmaps[index] : null

  const go = (delta: number) => {
    if (roadmaps.length === 0) return
    const from = index >= 0 ? index : 0
    const next = (from + delta + roadmaps.length) % roadmaps.length
    navigate(`/roadmaps/${roadmaps[next].id}`)
  }

  if (loading) {
    return (
      <PageContainer width="reading">
        <PageHeader title="Career roadmap" sub="Loading roadmap…" />
        <div className="rounded-2xl border border-campus-900/[0.08] bg-white p-8 shadow-card">
          <div className="mb-4 h-8 w-2/3 rounded-xl bg-surface-100" />
          <div className="mb-2 h-4 w-full rounded bg-surface-100" />
          <div className="h-4 w-4/5 rounded bg-surface-100" />
        </div>
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer width="reading">
        <PageHeader title="Roadmap unavailable" sub={error} />
        <Button variant="outline" onClick={() => navigate(-1)}>
          Go back
        </Button>
      </PageContainer>
    )
  }

  if (!current) {
    return (
      <PageContainer width="reading">
        <PageHeader
          title="No roadmaps yet"
          sub="Alumni publish these from their dashboard. Check back soon."
        />
        <Link to="/placement-training">
          <Button variant="outline">
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Back to placement training
          </Button>
        </Link>
      </PageContainer>
    )
  }

  return (
    <PageContainer width="reading">
      <PageHeader
        title={current.title}
        sub={current.targetRole ? `Roadmap for ${current.targetRole}` : 'Career roadmap'}
        actions={
          roadmaps.length > 1 ? (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                aria-label="Previous roadmap"
                onClick={() => go(-1)}
              >
                <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              </Button>
              <span className="px-1 text-caption tabular-nums text-surface-500">
                {index + 1} / {roadmaps.length}
              </span>
              <Button variant="outline" size="sm" aria-label="Next roadmap" onClick={() => go(1)}>
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          ) : undefined
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>{current.title}</CardTitle>
          {current.targetRole && <CardDescription>{current.targetRole}</CardDescription>}
        </CardHeader>
        <CardContent>
          <p className="whitespace-pre-line text-body leading-relaxed text-surface-600">
            {current.description}
          </p>

          {(current.alumniName || current.alumniCompany) && (
            <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-1.5 border-t border-campus-900/[0.07] pt-4 text-caption text-surface-500">
              {current.alumniName && (
                <span className="inline-flex items-center gap-1.5">
                  <UserRound className="h-3.5 w-3.5" aria-hidden="true" />
                  {current.alumniName}
                  {current.alumniRole && <span className="text-surface-400"> · {current.alumniRole}</span>}
                </span>
              )}
              {current.alumniCompany && (
                <span className="inline-flex items-center gap-1.5">
                  <Building2 className="h-3.5 w-3.5" aria-hidden="true" />
                  {current.alumniCompany}
                </span>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {roadmaps.length > 1 && (
        <nav className="mt-5" aria-label="All roadmaps">
          <p className="mb-2 text-micro font-semibold uppercase tracking-wide text-surface-400">
            Other roadmaps
          </p>
          <div className="flex flex-wrap gap-2">
            {roadmaps.map((r) => (
              <Link
                key={r.id}
                to={`/roadmaps/${r.id}`}
                aria-current={r.id === current.id ? 'page' : undefined}
                className={cn(
                  'rounded-xl border px-3 py-1.5 text-body-sm transition-colors',
                  r.id === current.id
                    ? 'border-campus-300 bg-campus-50 font-semibold text-campus-700'
                    : 'border-campus-900/10 bg-white text-surface-600 hover:border-campus-300 hover:text-campus-700',
                )}
              >
                {r.title}
              </Link>
            ))}
          </div>
        </nav>
      )}
    </PageContainer>
  )
}
