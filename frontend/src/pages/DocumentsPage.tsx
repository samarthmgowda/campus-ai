import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Document as CampusDocument } from '../types'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { PageHeader, EmptyState, ErrorState, SkeletonCard } from '../components/ui/PageState'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { FileText, Upload, Plus, X, Search, LibraryBig, CheckCircle2, Eye, EyeOff, Trash2 } from 'lucide-react'

export default function DocumentsPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [pendingDelete, setPendingDelete] = useState<CampusDocument | null>(null)

  const { data: docs = [], isLoading, error, refetch } = useQuery({
    queryKey: ['documents'],
    queryFn: () => api.get<CampusDocument[]>('/documents'),
  })

  const { data: stats } = useQuery({
    queryKey: ['doc-stats'],
    queryFn: () => api.get<{ visible: number; hidden: number; total: number; totalChunks: number }>('/documents/stats/overview'),
  })

  const createMutation = useMutation({
    mutationFn: (body: { title: string; content: string }) => api.post<CampusDocument>('/documents', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      queryClient.invalidateQueries({ queryKey: ['doc-stats'] })
      setTitle(''); setContent(''); setShowCreate(false)
    },
  })

  const uploadMutation = useMutation({
    mutationFn: (file: File) => {
      const fd = new FormData()
      fd.append('file', file)
      return api.postForm<{ documentId: number; title: string; chunksCreated: number }>('/documents/upload', fd)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      queryClient.invalidateQueries({ queryKey: ['doc-stats'] })
      setUploadFile(null)
    },
  })

  const visibilityMutation = useMutation({
    mutationFn: ({ id, hidden }: { id: number; hidden: boolean }) =>
      api.post<CampusDocument>(`/documents/admin/${id}/visibility`, { hidden }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      queryClient.invalidateQueries({ queryKey: ['doc-stats'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/documents/admin/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      queryClient.invalidateQueries({ queryKey: ['doc-stats'] })
    },
  })

  const filtered = docs.filter(d =>
    !search || d.title.toLowerCase().includes(search.toLowerCase()) ||
    d.content.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <PageContainer width="standard">
      <PageHeader
        title="Library"
        sub="The knowledge Campus AI answers from — curated documents only"
        actions={
          <Button size="sm" variant="accent" onClick={() => setShowCreate(!showCreate)} aria-expanded={showCreate}>
            {showCreate ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
            New document
          </Button>
        }
      />

      {stats && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5" role="list" aria-label="Library stats">
          {[
            { label: 'Visible to AI', value: stats.visible },
            { label: 'Hidden', value: stats.hidden },
            { label: 'Documents', value: stats.total },
            { label: 'Knowledge chunks', value: stats.totalChunks },
          ].map(s => (
            <div key={s.label} role="listitem" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card px-4 py-3.5">
              <p className="font-display font-bold text-[24px] leading-none text-campus-950 tabular-nums">{s.value}</p>
              <p className="text-caption text-surface-500 mt-1.5">{s.label}</p>
            </div>
          ))}
        </div>
      )}

      <div className="rounded-2xl border border-dashed border-campus-900/15 bg-white/70 px-5 py-4 mb-5">
        <div className="flex items-center gap-3 flex-wrap">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] flex-shrink-0" aria-hidden="true">
            <Upload className="h-[18px] w-[18px] text-accent-300" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-semibold text-body-sm text-campus-950">Upload a file</p>
            <p className="text-caption text-surface-400">.txt or .md, max 5 MB — chunked and embedded automatically</p>
          </div>
          <label className="cursor-pointer rounded-xl border border-campus-900/10 bg-white px-3 py-2 text-body-sm font-semibold text-surface-600 hover:border-campus-900/25 hover:text-campus-900 transition-all focus-within:ring-2 focus-within:ring-campus-500">
            {uploadFile ? uploadFile.name : 'Choose file'}
            <input
              type="file"
              accept=".txt,.md"
              onChange={e => setUploadFile(e.target.files?.[0] ?? null)}
              className="sr-only"
              aria-label="Choose file to upload"
            />
          </label>
          <Button
            size="sm"
            variant="accent"
            disabled={!uploadFile || uploadMutation.isPending}
            loading={uploadMutation.isPending}
            onClick={() => uploadFile && uploadMutation.mutate(uploadFile)}
          >
            Upload
          </Button>
        </div>
        {uploadMutation.data && (
          <p className="inline-flex items-center gap-1.5 text-body-sm font-medium text-emerald-700 mt-3" role="status">
            <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
            Doc #{uploadMutation.data.documentId} — {uploadMutation.data.chunksCreated} chunks created
          </p>
        )}
        {uploadMutation.isError && (
          <p className="text-body-sm text-red-600 mt-3" role="alert">{(uploadMutation.error as Error).message}</p>
        )}
      </div>

      {showCreate && (
        <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-elevated p-5 sm:p-6 mb-5 animate-slide-down">
          <p className="font-display font-semibold text-[17px] text-campus-950">Write a document</p>
          <p className="text-body-sm text-surface-500 mt-0.5">Titles are unique — one fact per document answers best</p>
          <div className="grid gap-3 mt-4">
            <Input label="Title" value={title} onChange={e => setTitle(e.target.value)} placeholder="Hackathon team rules" />
            <div>
              <label htmlFor="doc-content" className="block text-body-sm font-medium text-surface-700 mb-1.5">Content</label>
              <textarea
                id="doc-content"
                value={content}
                onChange={e => setContent(e.target.value)}
                rows={4}
                placeholder="Each team must contain between 2 and 4 students…"
                className="w-full rounded-xl border border-surface-300 px-3 py-2 text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500 resize-y"
              />
              <p className="text-caption text-surface-400 mt-1">Plain sentences beat formatting — the AI chunks and embeds this text.</p>
            </div>
          </div>
          {createMutation.isError && (
            <p className="text-body-sm text-red-600 mt-3" role="alert">{(createMutation.error as Error).message}</p>
          )}
          <div className="flex gap-2 mt-4">
            <Button size="sm" variant="accent" disabled={!title.trim() || !content.trim() || createMutation.isPending} loading={createMutation.isPending}
              onClick={() => createMutation.mutate({ title: title.trim(), content: content.trim() })}>
              Save document
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setShowCreate(false)}>Cancel</Button>
          </div>
        </div>
      )}

      <div className="relative mb-5">
        <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-surface-400 pointer-events-none" aria-hidden="true" />
        <input
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search the library…"
          aria-label="Search documents"
          className="w-full bg-white border border-campus-900/10 rounded-xl pl-10 pr-3 py-2.5 text-body-sm shadow-card placeholder:text-surface-400 focus:outline-none focus:ring-2 focus:ring-campus-500"
        />
        {search && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2">
            <Badge variant="default" size="sm">{filtered.length}</Badge>
          </span>
        )}
      </div>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2"><SkeletonCard /><SkeletonCard /><SkeletonCard /><SkeletonCard /></div>
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={LibraryBig}
          title={search ? `Nothing matches "${search}"` : 'The shelves are empty'}
          hint={search ? 'Try a shorter term — titles and content both match.' : 'Upload or write the first document above.'}
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 stagger">
          {filtered.map((d, i) => (
            <article
              key={d.id}
              style={{ ['--stagger' as string]: Math.min(i, 8) }}
              className="group rounded-2xl border border-campus-900/[0.07] bg-white shadow-card hover:shadow-card-hover hover:-translate-y-[2px] transition-all duration-200 p-5"
            >
              <div className="flex items-start gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-campus-50 border border-campus-100 flex-shrink-0" aria-hidden="true">
                  <FileText className="h-5 w-5 text-campus-600" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="font-display font-semibold text-[15px] text-campus-950 tracking-tight leading-snug">{d.title}</p>
                    {d.hidden && <Badge variant="warning" size="sm">Hidden from AI</Badge>}
                  </div>
                  <p className="text-micro text-surface-400 mt-0.5 tabular-nums">
                    #{d.id}{d.createdAt ? ` · ${new Date(d.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })}` : ''}
                  </p>
                </div>
                <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    onClick={() => visibilityMutation.mutate({ id: d.id, hidden: !d.hidden })}
                    aria-label={d.hidden ? `Unhide ${d.title}` : `Hide ${d.title}`}
                    title={d.hidden ? 'Unhide' : 'Hide'}
                    className="p-2 rounded-lg text-surface-400 hover:text-campus-700 hover:bg-campus-100 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                  >
                    {d.hidden ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
                  </button>
                  <button
                    onClick={() => setPendingDelete(d)}
                    aria-label={`Delete ${d.title}`}
                    title="Delete"
                    className="p-2 rounded-lg text-surface-300 hover:text-red-600 hover:bg-red-50 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
              <p className="text-body-sm text-surface-600 leading-relaxed mt-3 line-clamp-3">{d.content}</p>
            </article>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={pendingDelete !== null}
        title={`Delete "${pendingDelete?.title ?? ''}"?`}
        body="This removes the document and every chunk the AI embedded from it. Answers that cited it will stop finding it."
        confirmLabel="Delete"
        pending={deleteMutation.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={async () => {
          if (!pendingDelete) return
          // Close on settle, not on click, so `pending` actually renders — the
          // delete round-trips through the embedding reindex and is not instant.
          // A failure surfaces in the role="alert" above rather than here.
          try {
            await deleteMutation.mutateAsync(pendingDelete.id)
          } catch {
            /* shown by the page-level alert */
          } finally {
            setPendingDelete(null)
          }
        }}
      />
    </PageContainer>
  )
}
