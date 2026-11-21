import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Select } from '../components/ui/Select'
import { Loader2 } from 'lucide-react'
import type { Event } from '../types'

interface SimpleEventForm {
  title: string
  type: string
  eventDate: string
  description: string
  location: string
}

function useSimpleEventForm() {
  const [form, setForm] = useState<SimpleEventForm>({
    title: '',
    type: 'HACKATHON',
    eventDate: '',
    description: '',
    location: '',
  })

  return { form, setForm }
}

export function SimpleEventCreate() {
  const { form, setForm } = useSimpleEventForm()
  const queryClient = useQueryClient()

  const createMutation = useMutation({
    mutationFn: (body: SimpleEventForm) => api.post<Event>('/events', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events'] })
      setForm({
        title: '',
        type: 'HACKATHON',
        eventDate: '',
        description: '',
        location: '',
      })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const body = {
      title: form.title.trim(),
      type: form.type.trim() || 'HACKATHON',
      eventDate: form.eventDate.trim(),
      description: form.description.trim(),
      location: form.location.trim(),
    }
    createMutation.mutate(body)
  }

  if (createMutation.isPending) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <Loader2 className="h-8 w-8 mx-auto animate-spin text-er-400" aria-hidden="true" />
        <p className="mt-4 text-body-sm text-ink-400">Creating event…</p>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6 max-w-lg">
      <div>
        <label className="text-body-sm font-medium text-ink-200">Title</label>
        <Input
          value={form.title}
          onChange={(e) =>
            setForm({ ...form, title: e.target.value })
          }
          placeholder="Event title"
          required
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="text-body-sm font-medium text-ink-200">Type</label>
          <Select
            options={[
              { value: 'HACKATHON', label: 'Hackathon' },
              { value: 'WORKSHOP', label: 'Workshop' },
              { value: 'SEMINAR', label: 'Seminar' },
              { value: 'NETWORKING', label: 'Networking' },
              { value: 'PLACEMENT', label: 'Placement Drive' },
            ]}
            value={form.type}
            onChange={(e) =>
              setForm({ ...form, type: (e.target as HTMLSelectElement).value })
            }
            className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
          />
        </div>

        <div>
          <label className="text-body-sm font-medium text-ink-200">Date</label>
          <Input
            type="date"
            value={form.eventDate}
            onChange={(e) =>
              setForm({ ...form, eventDate: e.target.value })
            }
            className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
          />
        </div>
      </div>

      <div>
        <label className="text-body-sm font-medium text-ink-200">Description</label>
        <Input
          value={form.description}
          onChange={(e) =>
            setForm({ ...form, description: e.target.value })
          }
          placeholder="What is happening?"
          className="w-full rounded-xl border border-white/12 bg-er-900 px-3 py-2 text-body-sm focus:outline-none focus:ring-2 focus:ring-er-500 resize-y"
          rows={3}
        />
      </div>

      <div>
        <label className="text-body-sm font-medium text-ink-200">Location</label>
        <Input
          value={form.location}
          onChange={(e) =>
            setForm({ ...form, location: e.target.value })
          }
          placeholder="Innovation Hall A"
          className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
        />
      </div>

      <div className="flex gap-2">
        <Button
          type="submit"
          variant="accent"
          disabled={createMutation.isPending}
          loading={createMutation.isPending}
        >
          Create Event
        </Button>
        <Button
          type="button"
          variant="ghost"
          onClick={() => setForm({
            title: '',
            type: 'HACKATHON',
            eventDate: '',
            description: '',
            location: '',
          })}
        >
          Cancel
        </Button>
      </div>
    </form>
  )
}