import { useState, useEffect } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { Map, Compass, Building2, User, ArrowLeft, ArrowRight, Youtube, BookOpen, FileText, Globe } from 'lucide-react'

interface RoadmapDto {
  id: number
  title: string
  targetRole: string
  description: string
  alumniId: number | null
  alumniName: string | null
  alumniRole: string | null
  alumniCompany: string | null
  resources?: Array<{ type: 'youtube' | 'blog' | 'pdf' | 'website'; url: string }>
}

interface AlumniOpt {
  id: number
  name: string
  currentRole: string | null
  currentCompany: string | null
}

export default function RoadmapPage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('')
  const [openId, setOpenId] = useState<number | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [roadmapIndex, setRoadmapIndex] = useState(0)
  const [roadmaps, setRoadmaps] = useState<RoadmapDto[]>([])
  const [alumniId, setAlumniId] = useState('')
  const [title, setTitle] = useState('')
  const [targetRole, setTargetRole] = useState('')
  const [description, setDescription] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const { data: roadmapsData = [], isLoading, error, refetch } = useQuery({
    queryKey: ['roadmaps', filter],
    queryFn: () => api.get<RoadmapDto[]>(`/roadmaps${filter.trim() ? `?role=${encodeURIComponent(filter.trim())}` : ''}`),
  })

  const { data: alumni = [] } = useQuery({
    queryKey: ['alumni-all'],
    queryFn: () => api.get<AlumniOpt[]>('/alumni'),
    enabled: showForm,
  })

  const goPrev = () => {
    if (roadmaps.length === 0) return
    setRoadmapIndex(prev => Math.max(0, prev - 1))
  }

  const goNext = () => {
    if (roadmaps.length === 0) return
    setRoadmapIndex(prev => Math.min(roadmaps.length - 1, prev + 1))
  }

  const total = roadmaps.length

  // Initialize roadmapIndex when data loads
  useEffect(() => {
    if (roadmapsData.length > 0) {
      setRoadmaps(roadmapsData)
      // If we already have an openId that corresponds to a roadmap, use it;
      // otherwise default to the first/last depending on index bounds
      const foundIndex = roadmapsData.findIndex((r: any) => r.id === openId)
      if (foundIndex >= 0) {
        setRoadmapIndex(foundIndex)
      } else if (roadmapIndex < 0 || roadmapIndex >= roadmaps.length) {
        setRoadmapIndex(0)
      }
    }
  }, [roadmapsData, openId, roadmapIndex, roadmaps.length])

  const createMutation = useMutation({
    mutationFn: (body: { alumniId: number; title: string; targetRole: string; description: string }) =>
      api.post<RoadmapDto>('/roadmaps', body),
    onSuccess: () => {
      setAlumniId(''); setTitle(''); setTargetRole(''); setDescription(''); setFormError(null); setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['roadmaps'] })
    },
    onError: (e: Error) => setFormError(e.message),
  })

  const submit = () => {
    setFormError(null)
    if (!alumniId) { setFormError('Pick the alumni author'); return }
    if (title.trim().length < 3) { setFormError('Title needs at least 3 characters'); return }
    if (!targetRole.trim()) { setFormError('Target role is required'); return }
    if (!description.trim()) { setFormError('Description is required'); return }
    createMutation.mutate({
      alumniId: Number(alumniId),
      title: title.trim(),
      targetRole: targetRole.trim(),
      description: description.trim(),
    })
  }

  return (
    <PageContainer width="reading">
      <PageHeader
        title="Career roadmaps"
        sub="Step-by-step guides written by alumni who walked the path"
        actions={
          <>
            <Button size="sm" variant="outline" onClick={goPrev} disabled={roadmapIndex === 0} aria-label="Previous roadmap">
              <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
            <span className="px-2 text-caption text-ink-400">
              {roadmapIndex + 1} / {total}
            </span>
            <Button size="sm" variant="outline" onClick={goNext} disabled={roadmapIndex >= roadmaps.length - 1} aria-label="Next roadmap">
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </>
        }
      />

      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-4 mb-5">
        <label htmlFor="roadmap-filter" className="block text-body-sm font-medium text-ink-200 mb-1.5">Filter by role</label>
        <Input
          id="roadmap-filter"
          value={filter}
          onChange={e => setFilter(e.target.value)}
          placeholder="e.g. backend, data science, design…"
        />
      </div>

      {showForm && (
        <div className="rounded-2xl border border-white/10 bg-er-800 shadow-elevated p-5 mb-4 animate-slide-down">
          <p className="font-display font-semibold text-[16px] text-ink-50">Publish a roadmap</p>
          <p className="text-caption text-ink-400 mt-0.5 mb-3">Alumni authorship — pick who wrote it</p>
          <label htmlFor="roadmap-alumni" className="block text-body-sm font-medium text-ink-200 mb-1.5">Alumni author</label>
          <select
            id="roadmap-alumni"
            value={alumniId}
            onChange={e => setAlumniId(e.target.value)}
            className="w-full rounded-xl border border-white/12 px-3 py-2.5 text-body-sm bg-er-900 focus:outline-none focus:ring-2 focus:ring-er-500 mb-3"
          >
            <option value="">Choose alumni…</option>
            {alumni.map(a => (
              <option key={a.id} value={a.id}>
                {a.name}{a.currentRole ? ` · ${a.currentRole}` : ''}{a.currentCompany ? ` @ ${a.currentCompany}` : ''}
              </option>
            ))}
          </select>
          <div className="space-y-3">
            <Input label="Title" value={title} onChange={e => setTitle(e.target.value)} placeholder="Backend Developer Roadmap" maxLength={150} />
            <Input label="Target role" value={targetRole} onChange={e => setTargetRole(e.target.value)} placeholder="Backend Engineer" />
            <div>
              <label htmlFor="roadmap-desc" className="block text-body-sm font-medium text-ink-200 mb-1.5">Step-by-step guide</label>
              <textarea
                id="roadmap-desc"
                value={description}
                onChange={e => setDescription(e.target.value)}
                rows={6}
                maxLength={5000}
                placeholder={'1. Learn Java + Spring Boot\n2. Build 2 REST projects\n3. …'}
                className="w-full rounded-xl border border-white/12 px-3 py-2.5 text-body-sm bg-er-900 focus:outline-none focus:ring-2 focus:ring-er-500 leading-relaxed"
              />
            </div>
          </div>
          {formError && <p className="text-body-sm text-red-300 mt-2" role="alert">{formError}</p>}
          <div className="flex gap-2 mt-3">
            <Button size="sm" variant="accent" disabled={createMutation.isPending} loading={createMutation.isPending} onClick={submit}>
              Publish roadmap
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setShowForm(false)}>Cancel</Button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-3"><SkeletonCard /><SkeletonCard /></div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : roadmaps.length === 0 ? (
        <EmptyState
          icon={Compass}
          title="No roadmaps yet"
          hint={filter ? 'Nothing matches that role — try another keyword.' : 'Alumni have not published any roadmaps yet — yours could be the first.'}
          action={!filter ? <Button size="sm" variant="accent" onClick={() => setShowForm(true)}>Publish the first</Button> : undefined}
        />
      ) : (
        <div className="space-y-2.5 stagger">
          {roadmaps.map((r, i) => {
            const open = openId === r.id
            return (
              <article
                key={r.id}
                className="rounded-2xl border border-white/10 bg-er-800 shadow-card px-4 py-3.5"
                style={{ animationDelay: `${i * 50}ms` }}
              >
                <button
                  className="w-full text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-er-500 rounded-lg"
                  onClick={() => setOpenId(open ? null : r.id)}
                  aria-expanded={open}
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-er-500 to-er-600 text-white flex-shrink-0" aria-hidden="true">
                      <Map className="h-5 w-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-display font-semibold text-[15px] text-ink-50 truncate">{r.title}</p>
                      <p className="text-caption text-ink-400 truncate">
                        {r.targetRole}
                        {r.alumniName ? ` · by ${r.alumniName}` : ''}
                        {r.alumniCompany ? ` @ ${r.alumniCompany}` : r.alumniRole ? ` · ${r.alumniRole}` : ''}
                      </p>
                    </div>
                    <Badge variant="default" size="sm">{open ? 'Hide' : 'View'}</Badge>
                  </div>
                </button>
                {open && (
                  <div className="mt-3 border-t border-white/10 pt-3 animate-fade-in">
                    {r.alumniName && (
                      <p className="flex items-center gap-1.5 text-caption text-ink-400 mb-2">
                        <User className="h-3.5 w-3.5" aria-hidden="true" />
                        {r.alumniName}
                        {r.alumniRole ? ` · ${r.alumniRole}` : ''}
                        {r.alumniCompany ? (
                          <span className="inline-flex items-center gap-1">
                            <Building2 className="h-3.5 w-3.5" aria-hidden="true" /> {r.alumniCompany}
                          </span>
                        ) : null}
                      </p>
                    )}
                    <p className="text-body-sm text-ink-300 leading-relaxed whitespace-pre-wrap">{r.description}</p>
                    {/* Resource area - revealed when expanded */}
                    {r.resources && r.resources.length > 0 && (
                      <div className="mt-4 p-3 bg-er-900/50 rounded-xl border border-er-500/20 fade-in">
                        <p className="text-caption text-er-400 mb-2">Resources</p>
                        <ul className="space-y-1.5 text-caption text-er-300">
                          {r.resources.map((res, resIdx) => (
                            <li key={resIdx} className="flex items-center gap-2">
                              {res.type === 'youtube' && (
                                <a
                                  href={res.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="flex items-center gap-1.5 text-er-400 hover:text-er-500 transition-colors"
                                >
                                  <Youtube className="h-3.5 w-3.5" />
                                  Watch on YouTube
                                </a>
                              )}
                              {res.type === 'blog' && (
                                <a
                                  href={res.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="flex items-center gap-1.5 text-er-400 hover:text-er-500 transition-colors"
                                >
                                  <BookOpen className="h-3.5 w-3.5" />
                                  Read blog
                                </a>
                              )}
                              {res.type === 'pdf' && (
                                <a
                                  href={res.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="flex items-center gap-1.5 text-er-400 hover:text-er-500 transition-colors"
                                >
                                  <FileText className="h-3.5 w-3.5" />
                                  Download PDF
                                </a>
                              )}
                              {res.type === 'website' && (
                                <a
                                  href={res.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="flex items-center gap-1.5 text-er-400 hover:text-er-500 transition-colors"
                                >
                                  <Globe className="h-3.5 w-3.5" />
                                  Visit website
                                </a>
                              )}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </article>
            )
          })}
        </div>
      )}
    </PageContainer>
  )
}
