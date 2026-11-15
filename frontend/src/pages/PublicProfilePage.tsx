import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader, LoadingState, ErrorState, EmptyState } from '../components/ui/PageState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Github, Linkedin, ArrowLeft, UserRound, UserX, Sparkles } from 'lucide-react'

interface PublicProfile {
  id: number
  name: string
  branch?: string | null
  year?: number | null
  skills?: string | null
  interests?: string | null
  bio?: string | null
  githubUrl?: string | null
  linkedinUrl?: string | null
  availability?: string | null
  connections?: Record<string, unknown>
  reputation?: Record<string, unknown>
  endorsements?: unknown
}

function splitCsv(value?: string | null): string[] {
  if (!value) return []
  return value.split(',').map((s) => s.trim()).filter(Boolean)
}

/**
 * Public view of another member, reached from a chat card. Read-only: it shows
 * what the campus already makes public (skills, interests, links) and nothing
 * private — no email, no DMs, no edit controls.
 */
export default function PublicProfilePage() {
  const { id } = useParams<{ id: string }>()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['public-profile', id],
    queryFn: () => api.get<PublicProfile>(`/profiles/${id}`),
    enabled: !!id,
  })

  if (!id) return <EmptyState icon={UserX} title="No member selected" hint="This link is missing a member id." />

  return (
    <PageContainer width="reading">
      <div className="mb-4">
        <Link
          to="/chat"
          className="inline-flex items-center gap-1.5 text-body-sm font-medium text-er-300 hover:text-er-300"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to chat
        </Link>
      </div>

      {isLoading ? (
        <LoadingState label="Loading profile…" />
      ) : error ? (
        <ErrorState title="Couldn't load this profile" message={(error as Error).message} onRetry={() => refetch()} />
      ) : !data ? (
        <EmptyState icon={UserX} title="Profile not found" hint="We couldn't find that member." />
      ) : (
        <>
          <PageHeader
            title={data.name || 'Member'}
            sub={[data.branch, data.year != null ? `Year ${data.year}` : null].filter(Boolean).join(' · ') || 'Campus member'}
          />

          <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-5 sm:p-6">
            <div className="flex items-start gap-4">
              <span
                className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-er-500 to-er-600 text-white font-display font-bold text-[26px] flex-shrink-0"
                aria-hidden="true"
              >
                {data.name ? data.name.charAt(0).toUpperCase() : <UserRound className="h-7 w-7" />}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  {data.availability && (
                    <Badge variant={data.availability === 'AVAILABLE' ? 'success' : 'outline'}>
                      {data.availability === 'AVAILABLE' ? 'Available' : data.availability}
                    </Badge>
                  )}
                  {data.branch && <Badge variant="info">{data.branch}</Badge>}
                  {data.year != null && <Badge variant="outline">Year {data.year}</Badge>}
                </div>
                {data.bio && <p className="mt-3 text-body-sm text-ink-300 leading-relaxed">{data.bio}</p>}
                {(data.githubUrl || data.linkedinUrl) && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {data.githubUrl && (
                      <a href={data.githubUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-body-sm font-medium text-er-300 hover:text-er-300">
                        <Github className="h-4 w-4" aria-hidden="true" /> GitHub
                      </a>
                    )}
                    {data.linkedinUrl && (
                      <a href={data.linkedinUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-body-sm font-medium text-er-300 hover:text-er-300">
                        <Linkedin className="h-4 w-4" aria-hidden="true" /> LinkedIn
                      </a>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>

          {splitCsv(data.skills).length > 0 && (
            <section aria-label="Skills" className="mt-4 rounded-2xl border border-white/10 bg-er-800 shadow-card p-5 sm:p-6">
              <p className="font-display font-semibold text-[16px] text-ink-50 mb-3">Skills</p>
              <div className="flex flex-wrap gap-2">
                {splitCsv(data.skills).map((s) => (
                  <span key={s} className="inline-flex items-center gap-1 rounded-full bg-er-900 border border-white/10 px-3 py-1 text-body-sm font-medium text-er-300">
                    <Sparkles className="h-3.5 w-3.5 text-er-300" aria-hidden="true" /> {s}
                  </span>
                ))}
              </div>
            </section>
          )}

          {splitCsv(data.interests).length > 0 && (
            <section aria-label="Interests" className="mt-4 rounded-2xl border border-white/10 bg-er-800 shadow-card p-5 sm:p-6">
              <p className="font-display font-semibold text-[16px] text-ink-50 mb-3">Interests</p>
              <div className="flex flex-wrap gap-2">
                {splitCsv(data.interests).map((s) => (
                  <span key={s} className="inline-flex items-center rounded-full bg-er-900 border border-white/10 px-3 py-1 text-body-sm text-ink-300">
                    {s}
                  </span>
                ))}
              </div>
            </section>
          )}

          <div className="mt-5">
            <Link to="/messages">
              <Button variant="outline" size="sm">Message on Campus Connect</Button>
            </Link>
          </div>
        </>
      )}
    </PageContainer>
  )
}
