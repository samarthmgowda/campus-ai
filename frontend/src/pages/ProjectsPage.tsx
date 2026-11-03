import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { FolderKanban, Plus, UserPlus, X, Cpu, Globe, Leaf, Palette, Cog, FlaskConical, Search } from 'lucide-react'
import { cn } from '../utils/cn'

interface ProjectDto {
  id: number
  name: string
  description: string
  domain: string
  requiredSkills: string
  status: string
  ownerName: string
  members: { studentId: number; name: string; role: string }[]
}

function domainStyle(domain: string) {
  const d = (domain ?? '').toUpperCase()
  if (d.includes('AI') || d.includes('ML') || d.includes('DATA')) return { Icon: Cpu, bar: 'from-[#667eea] to-[#764ba2]', chip: 'bg-campus-50 text-campus-700 border-campus-100' }
  if (d.includes('WEB') || d.includes('APP') || d.includes('SOFTWARE')) return { Icon: Globe, bar: 'from-sky-400 to-sky-600', chip: 'bg-sky-50 text-sky-700 border-sky-100' }
  if (d.includes('ENV') || d.includes('GREEN') || d.includes('AGRI')) return { Icon: Leaf, bar: 'from-emerald-400 to-emerald-600', chip: 'bg-emerald-50 text-emerald-700 border-emerald-100' }
  if (d.includes('DESIGN') || d.includes('UI') || d.includes('MEDIA')) return { Icon: Palette, bar: 'from-accent-400 to-accent-600', chip: 'bg-accent-50 text-accent-700 border-accent-100' }
  if (d.includes('ROBOT') || d.includes('IOT') || d.includes('HARDWARE')) return { Icon: Cog, bar: 'from-violet-400 to-violet-600', chip: 'bg-violet-50 text-violet-700 border-violet-100' }
  return { Icon: FlaskConical, bar: 'from-amber-400 to-amber-600', chip: 'bg-amber-50 text-amber-700 border-amber-100' }
}

export default function ProjectsPage() {
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [domain, setDomain] = useState('AI')
  const [skills, setSkills] = useState('')
  const [addMemberFor, setAddMemberFor] = useState<number | null>(null)
  const [memberSearch, setMemberSearch] = useState('')
  const [searchResults, setSearchResults] = useState<{ id: number; name: string; branch: string; skills: string }[]>([])

  const { data: projects = [], isLoading, error, refetch } = useQuery({
    queryKey: ['my-projects'],
    queryFn: () => api.get<ProjectDto[]>('/projects/mine'),
  })

  // Search students for invitation
  const searchStudents = async (query: string) => {
    if (query.trim().length < 2) {
      setSearchResults([])
      return
    }
    try {
      // Reusing the general students search endpoint
      const all = await api.get<{ id: number; name: string; branch: string; skills: string }[]>('/students')
      const filtered = all.filter(s =>
        s.name.toLowerCase().includes(query.toLowerCase()) ||
        (s.skills && s.skills.toLowerCase().includes(query.toLowerCase()))
      ).slice(0, 5)
      setSearchResults(filtered)
    } catch (err) {
      console.error('Search failed', err)
    }
  }

  const createMutation = useMutation({
    mutationFn: (body: Record<string, string>) => api.post<ProjectDto>('/projects', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-projects'] })
      setName(''); setDescription(''); setDomain('AI'); setSkills(''); setShowCreate(false)
    },
  })

  const addMemberMutation = useMutation({
    mutationFn: ({ projectId, studentId }: { projectId: number; studentId: number }) =>
      api.post(`/projects/${projectId}/members`, { studentId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-projects'] })
      setAddMemberFor(null); setMemberSearch(''); setSearchResults([])
    },
  })

  const active = projects.filter(p => p.status === 'ACTIVE').length

  return (
    <PageContainer width="standard">
      <PageHeader
        title="Projects"
        sub={projects.length > 0 ? `${active} active · ${projects.length} total you own or joined` : 'Build together, ship together'}
        actions={
          <Button size="sm" variant="accent" onClick={() => setShowCreate(!showCreate)} aria-expanded={showCreate}>
            {showCreate ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
            New project
          </Button>
        }
      />

      {showCreate && (
        <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-elevated p-5 sm:p-6 mb-6 animate-slide-down">
          <p className="font-display font-semibold text-[17px] text-campus-950">Start a project</p>
          <p className="text-body-sm text-surface-500 mt-0.5">Names are unique across campus — pick one that sticks</p>
          <div className="grid gap-3 sm:grid-cols-2 mt-4">
            <Input label="Name" value={name} onChange={e => setName(e.target.value)} placeholder="Campus AI" />
            <div className="w-full">
              <label htmlFor="proj-domain" className="block text-body-sm font-medium text-surface-700 mb-1.5">Domain</label>
              <select
                id="proj-domain"
                value={domain}
                onChange={e => setDomain(e.target.value)}
                className="w-full rounded-lg border border-surface-300 bg-white px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-campus-500 transition-colors"
              >
                <option value="AI">AI / Machine Learning</option>
                <option value="Web">Web Development</option>
                <option value="Mobile">Mobile App</option>
                <option value="Design">UI/UX Design</option>
                <option value="IoT">IoT / Hardware</option>
                <option value="Green">Environment / Green Tech</option>
                <option value="Research">Research / Academic</option>
              </select>
            </div>
            <Input label="Skills needed" value={skills} onChange={e => setSkills(e.target.value)} placeholder="Java, React" helperText="Separate skills with commas" />
            <div>
              <label htmlFor="proj-desc" className="block text-body-sm font-medium text-surface-700 mb-1.5">What are you building?</label>
              <textarea
                id="proj-desc"
                value={description}
                onChange={e => setDescription(e.target.value)}
                rows={2}
                placeholder="One sharp paragraph — goal, stack, who you need…"
                className="w-full rounded-xl border border-surface-300 px-3 py-2 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500 resize-none"
              />
            </div>
          </div>
          {createMutation.isError && (
            <p className="text-body-sm text-red-600 mt-3" role="alert">{(createMutation.error as Error).message}</p>
          )}
          <div className="flex gap-2 mt-4">
            <Button size="sm" variant="accent" disabled={!name.trim() || createMutation.isPending} loading={createMutation.isPending}
              onClick={() => createMutation.mutate({ name: name.trim(), description: description.trim(), domain: domain.trim(), requiredSkills: skills.trim() })}>
              Create project
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setShowCreate(false)}>Cancel</Button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2"><SkeletonCard /><SkeletonCard /><SkeletonCard /><SkeletonCard /></div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : projects.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title="No projects yet"
          hint="Create your first project above — teammates, skills, and momentum start here."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 stagger">
          {projects.map((p, i) => {
            const { Icon, bar, chip } = domainStyle(p.domain)
            const skillList = (p.requiredSkills ?? '').split(',').map(s => s.trim()).filter(Boolean)
            return (
              <article
                key={p.id}
                style={{ ['--stagger' as string]: Math.min(i, 8) }}
                className="group rounded-2xl border border-campus-900/[0.07] bg-white shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 overflow-hidden"
              >
                <div className={cn('h-1.5 bg-gradient-to-r', bar)} aria-hidden="true" />
                <div className="p-5">
                  <div className="flex items-start gap-3">
                    <span className={cn('flex h-11 w-11 items-center justify-center rounded-xl border flex-shrink-0', chip)} aria-hidden="true">
                      <Icon className="h-5 w-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="font-display font-semibold text-[16px] text-campus-950 tracking-tight">{p.name}</p>
                        <Badge variant={p.status === 'ACTIVE' ? 'success' : 'outline'} size="sm">{p.status}</Badge>
                      </div>
                      <p className="text-caption text-surface-400 mt-0.5">{p.domain} · by {p.ownerName}</p>
                    </div>
                  </div>
                  {p.description && <p className="text-body-sm text-surface-600 mt-3 leading-relaxed line-clamp-2">{p.description}</p>}
                  {skillList.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mt-3">
                      {skillList.slice(0, 5).map(s => (
                        <span key={s} className="rounded-full bg-campus-50 border border-campus-100 px-2.5 py-0.5 text-micro font-medium text-campus-700">{s}</span>
                      ))}
                      {skillList.length > 5 && (
                        <span className="text-micro text-surface-400 px-1 py-0.5">+{skillList.length - 5}</span>
                      )}
                    </div>
                  )}
                  <div className="flex items-center justify-between mt-4 pt-3.5 border-t border-campus-900/[0.06]">
                    <div className="flex items-center" aria-label={`${p.members.length} members`}>
                      {p.members.slice(0, 4).map(m => (
                        <span
                          key={m.studentId}
                          title={m.name}
                          className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white text-micro font-bold ring-2 ring-white -ml-1.5 first:ml-0"
                          aria-hidden="true"
                        >
                          {m.name.charAt(0).toUpperCase()}
                        </span>
                      ))}
                      <span className="text-caption text-surface-400 ml-2 tabular-nums">
                        {p.members.length} member{p.members.length === 1 ? '' : 's'}
                      </span>
                    </div>
                    {addMemberFor === p.id ? (
                      <div className="flex flex-col gap-2 w-full max-w-[200px] relative">
                        <div className="relative">
                          <input
                            value={memberSearch}
                            onChange={e => {
                              setMemberSearch(e.target.value)
                              searchStudents(e.target.value)
                            }}
                            placeholder="Search teammate..."
                            className="w-full rounded-lg border border-surface-300 px-2 py-1.5 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500 pl-7"
                          />
                          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-surface-400" />
                        </div>
                        {searchResults.length > 0 && (
                          <div className="absolute z-10 w-full bg-white border border-campus-900/[0.1] rounded-xl shadow-elevated overflow-hidden animate-slide-down top-full mt-1">
                            {searchResults.map(s => (
                              <button
                                key={s.id}
                                onClick={() => {
                                  addMemberMutation.mutate({ projectId: p.id, studentId: s.id })
                                }}
                                className="w-full text-left px-3 py-2 hover:bg-campus-50 flex items-center gap-2 transition-colors"
                              >
                                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-campus-100 text-campus-700 text-[10px] font-bold">
                                  {s.name.charAt(0).toUpperCase()}
                                </span>
                                <div className="min-w-0 overflow-hidden">
                                  <p className="text-body-sm font-medium text-campus-950 truncate">{s.name}</p>
                                  <p className="text-micro text-surface-400 truncate">{s.branch}</p>
                                </div>
                              </button>
                            ))}
                          </div>
                        )}
                        <div className="flex gap-1.5">
                          <Button size="sm" variant="ghost" onClick={() => {
                            setAddMemberFor(null);
                            setMemberSearch('');
                            setSearchResults([]);
                          }}>Cancel</Button>
                        </div>
                      </div>
                    ) : (
                      <button
                        onClick={() => setAddMemberFor(p.id)}
                        className="inline-flex items-center gap-1 text-body-sm font-semibold text-surface-400 hover:text-campus-700 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500 rounded-lg px-2 py-1"
                      >
                        <UserPlus className="h-4 w-4" /> Invite
                      </button>
                    )}
                  </div>
                  {addMemberMutation.isError && addMemberFor === p.id && (
                    <p className="text-body-sm text-red-600 mt-2" role="alert">{(addMemberMutation.error as Error).message}</p>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}
    </PageContainer>
  )
}
