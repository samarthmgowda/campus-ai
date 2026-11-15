import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Loader2, X } from 'lucide-react'
import type { Event } from '../types'

interface EventTemplate {
  id: number
  templateName: string
  formConfig: string | null  // JSON config for the form fields
  isActive: boolean
  createdAt: string
  usesPgvector: boolean | null
}

interface TemplateFormFields {
  [key: string]: {
    label: string
    type: 'text' | 'textarea' | 'select' | 'date'
    placeholder?: string
    options?: string[]
  }
}

interface UseTemplateResult {
  templates: EventTemplate[]
  isLoading: boolean
  isError: boolean
  error: Error | null
}

export function useEventTemplates(): UseTemplateResult {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['event-templates'],
    queryFn: () => api.get<EventTemplate[]>('/event-templates'),
    staleTime: 60_000,
  })

  return { templates: data ?? [], isLoading, isError, error }
}

export function useTemplateCreateMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post<Event>('/events', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })
}

function parseFormConfig(configJson: string | null): TemplateFormFields {
  if (!configJson) return {}
  try {
    const config = JSON.parse(configJson)
    const fields: TemplateFormFields = {}
    if (config.fields && Array.isArray(config.fields)) {
      for (const field of config.fields) {
        fields[field.name] = {
          label: field.label || field.name,
          type: field.type || 'text',
          placeholder: field.placeholder,
          options: field.options,
        }
      }
    }
    return fields
  } catch {
    return {}
  }
}

function TemplateFieldRow({
  field,
  onChange,
  onDelete,
  fieldConfig,
}: {
  field: keyof TemplateFormFields
  fieldConfig: TemplateFormFields[keyof TemplateFormFields]
  onChange: (field: keyof TemplateFormFields, value: string) => void
  onDelete: (field: keyof TemplateFormFields) => void
}) {
  const { label, type, placeholder, options } = fieldConfig ?? {}

  if (type === 'select' && options && options.length > 0) {
    return (
      <div className="space-y-1.5">
        <label className="text-body-sm font-medium text-ink-200">{label}</label>
        <select
          className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
        >
          <option value="">-- Select --</option>
          {options.map((opt: string) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
        <Button
          size="sm"
          variant="ghost"
          className="mt-1.5 rounded-md p-1.5 hover:bg-er-700"
          onClick={() => onDelete(field)}
        >
          <X className="h-3 w-3" aria-hidden="true" />
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-1.5">
      <label className="text-body-sm font-medium text-ink-200">{label}</label>
      {type === 'textarea' ? (
        <textarea
          className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body-sm resize-none focus:outline-none focus:ring-2 focus:ring-er-500"
          placeholder={placeholder || ''}
          rows={3}
          onChange={(e) => onChange(field, e.target.value)}
        />
      ) : type === 'date' ? (
        <input
          type="date"
          className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
          placeholder={placeholder || ''}
          onChange={(e) => onChange(field, e.target.value)}
        />
      ) : (
        <input
          type="text"
          className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500"
          placeholder={placeholder || ''}
          value=""
          onChange={(e) => onChange(field, e.target.value)}
        />
      )}
      <Button
        size="sm"
        variant="ghost"
        className="mt-1.5 rounded-md p-1.5 hover:bg-er-700"
        onClick={() => onDelete(field)}
      >
        <X className="h-3 w-3" aria-hidden="true" />
      </Button>
    </div>
  )
}

export function DragEventBuilder() {
  const { templates, isLoading, isError, error } = useEventTemplates()
  const createMutation = useTemplateCreateMutation()
  const queryClient = useQueryClient()

  const [selectedTemplate, setSelectedTemplate] = useState<EventTemplate | null>(null)
  const [formValues, setFormValues] = useState<Record<string, string>>({})
  const [formFields, setFormFields] = useState<TemplateFormFields>({})

  // Parse form config when template is selected
  useEffect(() => {
    if (selectedTemplate) {
      const parsed = parseFormConfig(selectedTemplate.formConfig ?? '')
      setFormFields(parsed)
    }
  }, [selectedTemplate])

  const handleCreate = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedTemplate || createMutation.isPending) return

    const body: Record<string, string> = {
      title: formValues.title || selectedTemplate.templateName,
      type: formValues.type || 'HACKATHON',
      eventDate: formValues.eventDate || '',
      description: formValues.description || '',
      location: formValues.location || '',
    }

    await createMutation.mutateAsync(body)
    setSelectedTemplate(null)
    setFormValues({})
    setFormFields({})
    queryClient.invalidateQueries({ queryKey: ['events'] })
  }

  if (isLoading) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <Loader2 className="h-8 w-8 mx-auto animate-spin text-er-400" aria-hidden="true" />
        <p className="mt-4 text-body-sm text-ink-400">Loading event templates…</p>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <p className="text-body-sm text-red-300">{error?.message}</p>
        <Button size="sm" variant="accent" onClick={() => setSelectedTemplate(null)}>Retry</Button>
      </div>
    )
  }

  // No templates available - show add template option
  if (!templates.length) {
    return (
      <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-8 text-center">
        <p className="text-body-sm text-ink-400 mb-4">
          No event templates available. <Button size="sm" variant="accent" onClick={() => setSelectedTemplate(null)}>Create event anyway</Button>
        </p>
        <p className="text-caption text-ink-500">
          Ask an admin to create event templates, or create an event from scratch below.
        </p>
      </div>
    )
  }

  return (
    <div className="rounded-2xl border border-white/10 bg-er-800 shadow-card p-6 mb-6">
      <h2 className="font-display font-semibold text-[15px] text-ink-50 mb-4">
        Drag-and-Drop Event Builder
      </h2>

      {/* Template selector */}
      <div className="mb-6">
        <p className="text-body-sm text-ink-400 mb-2">Select a template</p>
        <select
          onChange={(e) => {
            const value = (e.target as HTMLSelectElement).value as string | undefined
            if (!value || value === '') {
              setSelectedTemplate(null)
            } else {
              setSelectedTemplate(t => t?.id === Number(value) ? null : templates.find(t => t.id === Number(value) || t.templateName === value) ?? null)
            }
          }}
          disabled={createMutation.isPending}
          className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500 appearance-none pr-9"
        >
          <option value="">-- Select --</option>
          {templates.map((t) => (
            <option key={t.id} value={t.templateName}>
              {t.templateName}
            </option>
          ))}
        </select>

        {selectedTemplate && (
          <p className="text-caption text-ink-500 mt-2">
            {selectedTemplate.formConfig ? 'Customize form below' : 'No form configuration — free form event creation'}
          </p>
        )}
      </div>

      {/* Builder form area */}
      {selectedTemplate && (
        <form onSubmit={e => {
          e.preventDefault()
          handleCreate(e)
        }} className="space-y-6">
          <div className="grid gap-3 sm:grid-cols-2">
            <Input
              label="Title"
              value={formValues.title || ''}
              onChange={(e) => setFormValues({ ...formValues, title: (e.target as HTMLInputElement).value })}
              placeholder="Event title"
            />
            <select
              value={formValues.type || 'HACKATHON'}
              onChange={(e) => setFormValues({ ...formValues, type: (e.target as HTMLSelectElement).value })}
              className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500 transition-colors"
            >
              <option value="HACKATHON">Hackathon</option>
              <option value="WORKSHOP">Workshop</option>
              <option value="SEMINAR">Seminar</option>
              <option value="NETWORKING">Networking</option>
              <option value="PLACEMENT">Placement Drive</option>
            </select>
          </div>

          {selectedTemplate.formConfig && formFields && Object.keys(formFields).length > 0 && (
            <div>
              <p className="text-body-sm text-ink-400">Template form fields:</p>
              {Object.entries(formFields).map(([name, config]) => (
                <TemplateFieldRow
                  key={name}
                  field={name as keyof TemplateFormFields}
                  fieldConfig={config}
                  onChange={(field, value) => setFormValues({ ...formValues, [field]: value })}
                  onDelete={() => setFormFields({ ...formFields, [name]: undefined as any })}
                />
              ))}
            </div>
          )}

          <Input
            label="Description"
            value={formValues.description || ''}
            onChange={(e) => setFormValues({ ...formValues, description: (e.target as HTMLInputElement).value })}
            placeholder="What is happening?"
            className="w-full rounded-xl border border-white/12 bg-er-900 px-3 py-2 text-body-sm focus:outline-none focus:ring-2 focus:ring-er-500 resize-y"
          />
          <Input
            label="Location"
            value={formValues.location || ''}
            onChange={(e) => setFormValues({ ...formValues, location: (e.target as HTMLInputElement).value })}
            placeholder="Innovation Hall A"
            className="w-full rounded-lg border border-white/12 bg-er-900 px-3 py-2 text-body focus:outline-none focus:ring-2 focus:ring-er-500 transition-colors"
          />

          <div className="flex gap-2">
            <Button
              type="submit"
              size="sm"
              variant="accent"
              disabled={createMutation.isPending}
              loading={createMutation.isPending}
            >
              Create Event from Template
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => {
                setSelectedTemplate(null)
                setFormValues({})
                setFormFields({})
              }}
            >
              Cancel
            </Button>
          </div>
        </form>
      )}

      {/* Fallback: create event from scratch */}
      {!selectedTemplate && (
        <p className="text-caption text-ink-400 mt-4">
          No template selected — <Button size="sm" variant="accent" onClick={() => setSelectedTemplate(null)}>Create event anyway (free form)</Button>
        </p>
      )}
    </div>
  )
}