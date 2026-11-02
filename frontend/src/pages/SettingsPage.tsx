import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { DemoUser } from '../types'
import { Button } from '../components/ui/Button'
import { Select } from '../components/ui/Select'
import { PageHeader } from '../components/ui/PageState'
import { getId, getRole, setIdentity, clearIdentity, authHeaderValue, ROLE_LABELS, type Role } from '../utils/identity'
import { LogOut, Server, Database, Cpu, UserRound } from 'lucide-react'

export default function SettingsPage() {
  const navigate = useNavigate()
  const [demoUser, setDemoUser] = useState('')

  const { data: users = [] } = useQuery({
    queryKey: ['demo-users'],
    queryFn: () => api.get<DemoUser[]>('/demo/users'),
  })

  useEffect(() => {
    const id = getId()
    if (id) setDemoUser(`${getRole() ?? 'student'}:${id}`)
  }, [])

  const handleUserChange = (key: string) => {
    setDemoUser(key)
    if (!key) {
      clearIdentity()
      return
    }
    const [role, id] = key.split(':')
    const user = users.find(u => `${u.role}:${u.id}` === key)
    setIdentity(id, role as Role, user?.name)
  }

  const handleLogout = () => {
    clearIdentity()
    navigate('/login')
  }

  const current = users.find(u => `${u.role}:${u.id}` === demoUser)
  const subtitle = current
    ? [current.branch, current.year != null ? `year ${current.year}` : null, current.company, current.currentRole]
        .filter(Boolean)
        .join(' · ')
    : ''

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Settings" sub="Identity and environment for this demo" />

      <section aria-label="Acting as" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden mb-4">
        <div className="flex items-center gap-4 p-5 sm:p-6">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold text-[22px] flex-shrink-0" aria-hidden="true">
            {current ? current.name.charAt(0).toUpperCase() : <UserRound className="h-6 w-6 text-accent-300" />}
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-micro font-semibold text-surface-400 tracking-wide">ACTING AS</p>
            <p className="font-display font-semibold text-[18px] text-campus-950 tracking-tight truncate mt-0.5">
              {current ? `${current.name}${subtitle ? ` · ${subtitle}` : ''}` : 'No user selected'}
            </p>
            <p className="text-caption text-surface-400 mt-0.5">
              Sent as <span className="font-medium text-surface-500">{authHeaderValue() ?? '—'}</span> on every request · admin allow-list is user 1
            </p>
          </div>
        </div>
        <div className="px-5 sm:px-6 pb-5 sm:pb-6">
          <Select
            label="Switch demo user"
            value={demoUser}
            onChange={e => handleUserChange(e.target.value)}
            options={users.map(u => ({
              value: `${u.role}:${u.id}`,
              label: `${u.name} (${ROLE_LABELS[u.role]}${u.company ? `, ${u.company}` : ''}${u.branch ? `, ${u.branch}` : ''})`,
            }))}
            placeholder="Select a user"
          />
          <div className="mt-4">
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="h-4 w-4" /> Sign out
            </Button>
          </div>
        </div>
      </section>

      <section aria-label="Backend" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5 sm:p-6">
        <p className="font-display font-semibold text-[16px] text-campus-950">Backend</p>
        <p className="text-caption text-surface-400 mt-0.5 mb-4">Connection details for this demo environment</p>
        <div className="space-y-2">
          {[
            { icon: Server, text: 'Spring Boot — http://localhost:8080' },
            { icon: Database, text: 'Postgres 18.6 + pgvector — localhost:5432' },
            { icon: Cpu, text: 'Ollama — bge-m3 (embed) + qwen2.5:3b (chat)' },
          ].map(r => (
            <div key={r.text} className="flex items-center gap-3 rounded-xl bg-campus-50/60 border border-campus-100/60 px-4 py-2.5 text-body-sm text-surface-700">
              <r.icon className="h-4 w-4 text-campus-500 flex-shrink-0" aria-hidden="true" />
              {r.text}
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
