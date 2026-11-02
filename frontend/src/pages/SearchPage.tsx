import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { PageHeader, EmptyState } from '../components/ui/PageState'
import { Badge } from '../components/ui/Badge'
import { Search, User, GraduationCap, FolderKanban, CalendarDays, FileText, ArrowUpRight, Loader2 } from 'lucide-react'
import { cn } from '../utils/cn'

interface Student { id: number; name: string; branch: string; skills: string | null; availability: string | null }
interface Alumni { id: number; name: string; branch: string; currentRole: string | null; currentCompany: string | null; skills: string | null }
interface Project { id: number; name: string; domain: string; description: string }
interface CampusEvent { id: number; title: string; type: string; eventDate: string }
interface DocHit { id: number; documentId: number; documentTitle: string; content: string; section: string | null; similarity: number }

const TABS = ['All', 'People', 'Projects', 'Events', 'Documents'] as const

function match(hay: (string | null | undefined)[], q: string) {
  const needle = q.trim().toLowerCase()
  if (!needle) return true
  return hay.some(h => (h ?? '').toLowerCase().includes(needle))
}

export default function SearchPage() {
  const [q, setQ] = useState('')
  const [tab, setTab] = useState<(typeof TABS)[number]>('All')
  const [submitted, setSubmitted] = useState('')

  const students = useQuery({ queryKey: ['search-students'], queryFn: () => api.get<Student[]>('/students'), staleTime: 60_000 })
  const alumni = useQuery({ queryKey: ['search-alumni'], queryFn: () => api.get<Alumni[]>('/alumni'), staleTime: 60_000 })
  const projects = useQuery({ queryKey: ['search-projects'], queryFn: () => api.get<Project[]>('/projects/mine'), staleTime: 60_000 })
  const events = useQuery({ queryKey: ['search-events'], queryFn: () => api.get<CampusEvent[]>('/events'), staleTime: 60_000 })
  const docs = useQuery({
    queryKey: ['search-docs', submitted],
    queryFn: () => api.get<DocHit[]>(`/search?q=${encodeURIComponent(submitted)}&limit=8`),
    enabled: submitted.trim().length > 1,
    staleTime: 60_000,
  })

  const needle = q.trim().toLowerCase()
  const peopleS = (students.data ?? []).filter(s => match([s.name, s.branch, s.skills], needle))
  const peopleA = (alumni.data ?? []).filter(a => match([a.name, a.branch, a.skills, a.currentRole, a.currentCompany], needle))
  const projF = (projects.data ?? []).filter(p => match([p.name, p.domain, p.description], needle))
  const evtF = (events.data ?? []).filter(e => match([e.title, e.type], needle))
  const totalPeople = peopleS.length + peopleA.length
  const showAll = tab === 'All' && needle.length > 0

  const loading = students.isLoading || alumni.isLoading

  return (
    <PageContainer width="reading">
      <PageHeader title="Search" sub="People, projects, events, and documents — one box" />

      <form
        onSubmit={e => { e.preventDefault(); setSubmitted(q) }}
        className="flex items-center gap-2 rounded-2xl border border-campus-900/10 bg-white p-2 pl-4 shadow-card focus-within:ring-2 focus-within:ring-campus-500 focus-within:border-transparent transition-shadow mb-4"
      >
        <Search className="h-5 w-5 text-surface-400 flex-shrink-0" aria-hidden="true" />
        <input
          value={q}
          onChange={e => setQ(e.target.value)}
          placeholder="Try a name, skill like React, or event type…"
          aria-label="Search campus"
          className="flex-1 min-w-0 bg-transparent text-body placeholder:text-surface-400 focus:outline-none"
        />
        {q && (
          <button
            type="button"
            onClick={() => { setQ(''); setSubmitted('') }}
            aria-label="Clear search"
            className="text-caption font-semibold text-surface-400 hover:text-surface-600 px-2"
          >
            Clear
          </button>
        )}
        <button
          type="submit"
          className="rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white px-4 py-2.5 text-body-sm font-semibold hover:bg-campus-900 active:scale-[0.98] transition-all"
        >
          Search
        </button>
      </form>

      <div className="flex gap-1.5 mb-5 flex-wrap" role="group" aria-label="Filter results">
        {TABS.map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            aria-pressed={tab === t}
            className={cn(
              'rounded-xl px-3.5 py-2 text-body-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-campus-500',
              tab === t ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white shadow-card' : 'bg-white border border-campus-900/10 text-surface-500 hover:text-campus-900'
            )}
          >
            {t}
          </button>
        ))}
      </div>

      {!needle ? (
        <EmptyState
          icon={Search}
          title="Search the whole campus"
          hint="Names, skills, branches, project domains, event types — plus semantic search over library documents when you hit Search."
        />
      ) : loading ? (
        <div className="flex justify-center py-12"><Loader2 className="h-8 w-8 animate-spin text-campus-500" /></div>
      ) : (
        <div className="space-y-5 stagger">
          {(tab === 'All' || tab === 'People') && (
            <section aria-label="People" style={{ ['--stagger' as string]: 0 }}>
              <p className="text-micro font-semibold text-surface-400 tracking-wide mb-2 px-1">PEOPLE · {totalPeople}</p>
              {totalPeople === 0 ? (
                <p className="text-body-sm text-surface-400 rounded-2xl border border-dashed border-campus-900/15 px-4 py-5 text-center">No people match “{q}”.</p>
              ) : (
                <div className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card divide-y divide-campus-900/[0.05] overflow-hidden">
                  {peopleS.slice(0, 5).map(s => (
                    <div key={`s-${s.id}`} className="flex items-center gap-3 px-4 py-3">
                      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold text-body-sm flex-shrink-0" aria-hidden="true">
                        {s.name.charAt(0).toUpperCase()}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold text-body-sm text-campus-950 truncate">{s.name}</p>
                        <p className="text-caption text-surface-400 truncate">{s.branch}{s.skills ? ` · ${s.skills.split(',').slice(0, 3).join(', ')}` : ''}</p>
                      </div>
                      <Badge variant="default" size="sm"><User className="h-3 w-3 mr-0.5" />Student</Badge>
                    </div>
                  ))}
                  {peopleA.slice(0, 5).map(a => (
                    <div key={`a-${a.id}`} className="flex items-center gap-3 px-4 py-3">
                      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-violet-400 to-violet-600 text-white font-display font-bold text-body-sm flex-shrink-0" aria-hidden="true">
                        {a.name.charAt(0).toUpperCase()}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold text-body-sm text-campus-950 truncate">{a.name}</p>
                        <p className="text-caption text-surface-400 truncate">
                          {a.currentRole ? `${a.currentRole}${a.currentCompany ? ` @ ${a.currentCompany}` : ''}` : a.branch}
                        </p>
                      </div>
                      <Badge variant="outline" size="sm"><GraduationCap className="h-3 w-3 mr-0.5" />Alumni</Badge>
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {(tab === 'All' || tab === 'Projects') && (
            <section aria-label="Projects" style={{ ['--stagger' as string]: 1 }}>
              <div className="flex items-center justify-between mb-2 px-1">
                <p className="text-micro font-semibold text-surface-400 tracking-wide">PROJECTS · {projF.length}</p>
                <Link to="/projects" className="text-body-sm font-semibold text-campus-600 hover:text-campus-800 inline-flex items-center gap-0.5">
                  Open <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              </div>
              {projF.length === 0 ? (
                <p className="text-body-sm text-surface-400 rounded-2xl border border-dashed border-campus-900/15 px-4 py-5 text-center">No projects match “{q}”.</p>
              ) : (
                <div className="space-y-2">
                  {(showAll ? projF.slice(0, 3) : projF).map(p => (
                    <div key={p.id} className="flex items-center gap-3 rounded-2xl border border-campus-900/[0.07] bg-white shadow-card px-4 py-3">
                      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-campus-50 border border-campus-100 flex-shrink-0" aria-hidden="true">
                        <FolderKanban className="h-4 w-4 text-campus-600" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold text-body-sm text-campus-950 truncate">{p.name}</p>
                        <p className="text-caption text-surface-400 truncate">{p.domain}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {(tab === 'All' || tab === 'Events') && (
            <section aria-label="Events" style={{ ['--stagger' as string]: 2 }}>
              <div className="flex items-center justify-between mb-2 px-1">
                <p className="text-micro font-semibold text-surface-400 tracking-wide">EVENTS · {evtF.length}</p>
                <Link to="/events" className="text-body-sm font-semibold text-campus-600 hover:text-campus-800 inline-flex items-center gap-0.5">
                  Open <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              </div>
              {evtF.length === 0 ? (
                <p className="text-body-sm text-surface-400 rounded-2xl border border-dashed border-campus-900/15 px-4 py-5 text-center">No events match “{q}”.</p>
              ) : (
                <div className="space-y-2">
                  {(showAll ? evtF.slice(0, 3) : evtF).map(e => (
                    <div key={e.id} className="flex items-center gap-3 rounded-2xl border border-campus-900/[0.07] bg-white shadow-card px-4 py-3">
                      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-accent-50 border border-accent-100 flex-shrink-0" aria-hidden="true">
                        <CalendarDays className="h-4 w-4 text-accent-600" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold text-body-sm text-campus-950 truncate">{e.title}</p>
                        <p className="text-caption text-surface-400 truncate">{e.eventDate}</p>
                      </div>
                      <Badge variant="info" size="sm">{e.type}</Badge>
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {(tab === 'All' || tab === 'Documents') && (
            <section aria-label="Documents" style={{ ['--stagger' as string]: 3 }}>
              <div className="flex items-center justify-between mb-2 px-1">
                <p className="text-micro font-semibold text-surface-400 tracking-wide">
                  DOCUMENTS{submitted ? ` · semantic match for “${submitted}”` : ''}
                </p>
                <Link to="/documents" className="text-body-sm font-semibold text-campus-600 hover:text-campus-800 inline-flex items-center gap-0.5">
                  Library <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              </div>
              {!submitted ? (
                <p className="text-body-sm text-surface-400 rounded-2xl border border-dashed border-campus-900/15 px-4 py-5 text-center">
                  Hit Search to run semantic matching over library documents.
                </p>
              ) : docs.isLoading ? (
                <div className="flex justify-center py-6"><Loader2 className="h-6 w-6 animate-spin text-campus-500" /></div>
              ) : docs.isError ? (
                <p className="text-body-sm text-red-600 rounded-2xl border border-red-200 bg-red-50/60 px-4 py-4" role="alert">
                  {(docs.error as Error).message}
                </p>
              ) : (docs.data ?? []).length === 0 ? (
                <p className="text-body-sm text-surface-400 rounded-2xl border border-dashed border-campus-900/15 px-4 py-5 text-center">
                  No document passages matched — try fewer or different words.
                </p>
              ) : (
                <div className="space-y-2">
                  {docs.data!.map(d => (
                    <div key={d.id} className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card px-4 py-3">
                      <div className="flex items-center gap-2">
                        <FileText className="h-4 w-4 text-campus-500 flex-shrink-0" aria-hidden="true" />
                        <p className="font-semibold text-body-sm text-campus-950 truncate flex-1">{d.documentTitle}</p>
                        <span className="text-micro font-semibold text-campus-600 tabular-nums flex-shrink-0">
                          {Math.round(d.similarity * 100)}%
                        </span>
                      </div>
                      <p className="text-body-sm text-surface-600 mt-1.5 line-clamp-2 leading-relaxed">{d.content}</p>
                      {d.section && <p className="text-micro text-surface-400 mt-1">§ {d.section}</p>}
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}
        </div>
      )}
    </PageContainer>
  )
}
