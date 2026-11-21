import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader, EmptyState, ErrorState } from '../components/ui/PageState'
import { Loader2 } from 'lucide-react'

export default function RoadmapPage() {
  const queryClient = useQueryClient()

  const { data: roadmaps = [], error, refetch } = useQuery({
    queryKey: ['roadmaps'],
    queryFn: () => api.get<any[]>(`/roadmaps`),
  })

  const createMutation = useMutation({
    mutationFn: (body: { title: string; description: string }) =>
      api.post<any>('/roadmaps', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roadmaps'] })
    },
  })

  if (createMutation.isPending) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <Button disabled>Publishing roadmap…</Button>
      </div>
    )
  }

  if (error) {
    return (
      <PageContainer width="reading">
        <PageHeader title="Roadmaps" sub="Unable to load roadmaps" />
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      </PageContainer>
    )
  }

  return (
    <PageContainer width="reading">
      <PageHeader
        title="Career roadmaps"
        sub="Step-by-step guides written by alumni who walked the path"
      />

      {roadmaps.length === 0 ? (
        <EmptyState icon={Loader2} title="No roadmaps yet" hint="Alumni have not published any roadmaps yet. Publish the first one above." />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {roadmaps.map((r) => (
            <div
              key={r.id}
              className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-4 hover:shadow-card-hover transition-all duration-200"
            >
              <h3 className="font-display font-semibold text-[15px] text-ink-50 mb-2">{r.title}</h3>
              <p className="text-body-sm text-ink-300 line-clamp-2">{r.description}</p>
              <Button size="sm" variant="outline" onClick={() => window.location.href = `/roadmaps/${r.id}`}>
                View details
              </Button>
            </div>
          ))}
        </div>
      )}
    </PageContainer>
  )
}