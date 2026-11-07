import { useState, useEffect, useRef } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { getId } from '../utils/identity'
import type { Student } from '../types'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { PageHeader, LoadingState, EmptyState, ErrorState } from '../components/ui/PageState'
import { cn } from '../utils/cn'
import { Send, Loader2, MessagesSquare, Plus, CheckCheck, X } from 'lucide-react'

interface MessageDto {
  id: number
  senderId: number | null
  senderName: string | null
  recipientId: number | null
  recipientName: string | null
  content: string
  createdAt: string | null
  readFlag: boolean
}

interface ConversationDto {
  peerId: number
  peerName: string | null
  lastMessageId: number | null
  preview: string | null
  lastAt: string | null
  direction: string
  unreadCount: number
}

function myId(): number | null {
  const v = getId()
  return v ? Number(v) : null
}

function initialOf(name: string | null, id: number) {
  return ((name ?? `#${id}`).trim().charAt(0) || '?').toUpperCase()
}

function threadTime(iso: string | null) {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    const today = new Date()
    const sameDay = d.toDateString() === today.toDateString()
    if (sameDay) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

export default function MessagesPage() {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<{ id: number; name: string } | null>(null)
  const [draft, setDraft] = useState('')
  const [showNew, setShowNew] = useState(false)
  const [newPeerId, setNewPeerId] = useState('')
  const [newMessage, setNewMessage] = useState('')
  const threadEndRef = useRef<HTMLDivElement>(null)
  const me = myId()

  const { data: inbox = [], isLoading, error, refetch } = useQuery({
    queryKey: ['inbox'],
    queryFn: () => api.get<ConversationDto[]>('/messages/inbox'),
  })

  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => api.get<Student[]>('/users'),
  })

  const { data: thread = [], isLoading: threadLoading } = useQuery({
    queryKey: ['thread', selected?.id],
    queryFn: () => api.get<MessageDto[]>(`/messages/thread/${selected!.id}`),
    enabled: selected !== null,
  })

  const markRead = useMutation({
    mutationFn: (peerId: number) => api.post(`/messages/thread/${peerId}/read`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['inbox'] }),
  })

  useEffect(() => {
    if (selected) markRead.mutate(selected.id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.id])

  useEffect(() => {
    threadEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [thread.length, selected?.id])

  const sendMutation = useMutation({
    mutationFn: ({ recipientId, content }: { recipientId: number; content: string }) =>
      api.post<MessageDto>('/messages', { recipientId, content }),
    onSuccess: () => {
      setDraft('')
      queryClient.invalidateQueries({ queryKey: ['thread', selected?.id] })
      queryClient.invalidateQueries({ queryKey: ['inbox'] })
    },
  })

  const newMutation = useMutation({
    mutationFn: ({ recipientId, content }: { recipientId: number; content: string }) =>
      api.post<MessageDto>('/messages', { recipientId, content }),
    onSuccess: (msg) => {
      setNewPeerId(''); setNewMessage(''); setShowNew(false)
      queryClient.invalidateQueries({ queryKey: ['inbox'] })
      if (msg.recipientId) setSelected({ id: msg.recipientId, name: msg.recipientName ?? `User #${msg.recipientId}` })
    },
  })

  const peers = users.filter(u => u.id !== me)
  const totalUnread = inbox.reduce((s, c) => s + (c.unreadCount ?? 0), 0)

  return (
    <PageContainer width="standard">
      <PageHeader
        title="Messages"
        sub={totalUnread > 0 ? `${totalUnread} unread across ${inbox.length} conversations` : 'Direct lines to your peers'}
        actions={
          <Button size="sm" variant="accent" onClick={() => setShowNew(!showNew)} aria-expanded={showNew}>
            {showNew ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
            New message
          </Button>
        }
      />

      {showNew && (
        <div className="rounded-2xl border border-campus-900/[0.08] bg-white shadow-card p-4 mb-5 animate-slide-down">
          <div className="grid gap-3 sm:grid-cols-[220px_1fr_auto] sm:items-end">
            <div>
              <label htmlFor="new-peer" className="block text-body-sm font-medium text-surface-700 mb-1.5">To</label>
              <select
                id="new-peer"
                value={newPeerId}
                onChange={e => setNewPeerId(e.target.value)}
                className="w-full rounded-xl border border-surface-300 px-3 py-2 text-body-sm bg-white focus:outline-none focus:ring-2 focus:ring-campus-500"
              >
                <option value="">Choose a student…</option>
                {peers.map(u => (
                  <option key={u.id} value={u.id}>{u.name} · {u.branch}</option>
                ))}
              </select>
            </div>
            <Input label="Message" value={newMessage} onChange={e => setNewMessage(e.target.value)} placeholder="Say hello…" />
            <Button
              size="sm"
              variant="accent"
              disabled={!newPeerId || !newMessage.trim() || newMutation.isPending}
              loading={newMutation.isPending}
              onClick={() => newMutation.mutate({ recipientId: Number(newPeerId), content: newMessage.trim() })}
            >
              <Send className="h-4 w-4" /> Send
            </Button>
          </div>
          {newMutation.isError && (
            <p className="text-body-sm text-red-600 mt-2" role="alert">{(newMutation.error as Error).message}</p>
          )}
        </div>
      )}

      {isLoading ? (
        <LoadingState label="Loading conversations…" />
      ) : error ? (
        <ErrorState message={(error as Error).message} onRetry={() => refetch()} />
      ) : inbox.length === 0 ? (
        <EmptyState
          icon={MessagesSquare}
          title="No conversations yet"
          hint="Start a direct conversation with a peer — it will show up here."
          action={<Button size="sm" variant="accent" onClick={() => setShowNew(true)}><Plus className="h-4 w-4" /> New message</Button>}
        />
      ) : (
        <div className="grid gap-5 lg:grid-cols-[300px_1fr] items-start">
          {/* Conversation list */}
          <div className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden">
            <div className="px-4 py-3 border-b border-campus-900/[0.06] bg-campus-50/50">
              <p className="text-micro font-semibold text-surface-500 tracking-wide">INBOX · {inbox.length}</p>
            </div>
            <div role="listbox" aria-label="Conversations" className="p-2 space-y-0.5 max-h-[480px] overflow-y-auto scrollbar-light">
              {inbox.map(c => {
                const active = selected?.id === c.peerId
                return (
                  <button
                    key={c.peerId}
                    role="option"
                    aria-selected={active}
                    onClick={() => setSelected({ id: c.peerId, name: c.peerName ?? `User #${c.peerId}` })}
                    className={cn(
                      'w-full text-left rounded-xl px-3 py-3 transition-all focus:outline-none focus:ring-2 focus:ring-campus-500 flex items-center gap-3',
                      active ? 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white shadow-card' : 'hover:bg-campus-50'
                    )}
                  >
                    <span className="relative flex-shrink-0" aria-hidden="true">
                      <span className={cn(
                        'flex h-10 w-10 items-center justify-center rounded-full font-display font-bold text-body-sm',
                        active ? 'bg-white/15 text-white' : 'bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white'
                      )}>
                        {initialOf(c.peerName, c.peerId)}
                      </span>
                      {c.unreadCount > 0 && (
                        <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-accent-500 text-white text-micro font-bold flex items-center justify-center tabular-nums ring-2 ring-white">
                          {c.unreadCount > 9 ? '9+' : c.unreadCount}
                        </span>
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline justify-between gap-2">
                        <span className={cn('font-semibold text-body-sm truncate', active ? 'text-white' : 'text-campus-950')}>
                          {c.peerName ?? `User #${c.peerId}`}
                        </span>
                        <span className={cn('text-micro flex-shrink-0 tabular-nums', active ? 'text-white/60' : 'text-surface-400')}>
                          {threadTime(c.lastAt)}
                        </span>
                      </span>
                      <span className={cn('block text-caption truncate mt-0.5', active ? 'text-white/70' : 'text-surface-400')}>
                        {c.preview || 'No messages yet'}
                      </span>
                    </span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Thread */}
          <div className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden flex flex-col min-h-[480px]">
            {!selected ? (
              <div className="flex-1 flex flex-col items-center justify-center text-center py-16 px-6">
                <span className="flex h-[52px] w-[52px] items-center justify-center rounded-2xl bg-campus-50 border border-campus-100 mb-4">
                  <MessagesSquare className="h-6 w-6 text-campus-500" aria-hidden="true" />
                </span>
                <p className="font-display font-semibold text-[17px] text-campus-950">Pick a conversation</p>
                <p className="text-body-sm text-surface-500 mt-1">Your thread appears here — unread messages mark read on open.</p>
              </div>
            ) : (
              <>
                <div className="px-5 py-3.5 border-b border-campus-900/[0.06] bg-campus-50/50 flex items-center gap-3">
                  <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold text-body-sm flex-shrink-0" aria-hidden="true">
                    {initialOf(selected.name, selected.id)}
                  </span>
                  <div className="min-w-0">
                    <p className="font-semibold text-body-sm text-campus-950 truncate">{selected.name}</p>
                    <p className="text-micro text-surface-400">Student · MIT Mysore</p>
                  </div>
                </div>
                <div className="flex-1 overflow-y-auto px-5 py-4 space-y-2 max-h-[400px] scrollbar-light" role="log" aria-live="polite" aria-label={`Messages with ${selected.name}`}>
                  {threadLoading ? (
                    <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-campus-500" /></div>
                  ) : thread.length === 0 ? (
                    <p className="text-body-sm text-surface-400 text-center py-10">No messages yet — say hello below.</p>
                  ) : (
                    thread.map(m => {
                      const mine = m.senderId === me
                      return (
                        <div key={m.id} className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
                          <div className={cn(
                            'max-w-[78%] px-4 py-2.5 text-body-sm animate-fade-in',
                            mine
                              ? 'rounded-2xl rounded-br-md bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white shadow-card'
                              : 'rounded-2xl rounded-bl-md bg-campus-50 border border-campus-100/70 text-surface-800'
                          )}>
                            <p className="whitespace-pre-wrap leading-relaxed">{m.content}</p>
                            <p className={cn('text-micro mt-1 flex items-center justify-end gap-1 tabular-nums', mine ? 'text-white/60' : 'text-surface-400')}>
                              {m.createdAt ? new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                              {mine && m.readFlag && <CheckCheck className="h-3.5 w-3.5" aria-label="Read" />}
                            </p>
                          </div>
                        </div>
                      )
                    })
                  )}
                  <div ref={threadEndRef} />
                </div>
                <div className="p-3 border-t border-campus-900/[0.06] bg-white">
                  {sendMutation.isError && (
                    <p className="text-body-sm text-red-600 mb-2" role="alert">{(sendMutation.error as Error).message}</p>
                  )}
                  <form
                    className="flex items-center gap-2 rounded-xl border border-campus-900/10 bg-campus-50/60 p-1.5 pl-4 focus-within:ring-2 focus-within:ring-campus-500 focus-within:bg-white transition-all"
                    onSubmit={e => {
                      e.preventDefault()
                      if (!draft.trim()) return
                      sendMutation.mutate({ recipientId: selected.id, content: draft.trim() })
                    }}
                  >
                    <input
                      value={draft}
                      onChange={e => setDraft(e.target.value)}
                      placeholder={`Reply to ${selected.name}…`}
                      aria-label={`Reply to ${selected.name}`}
                      className="flex-1 min-w-0 bg-transparent text-body-sm placeholder:text-surface-400 focus:outline-none"
                    />
                    <button
                      type="submit"
                      disabled={!draft.trim() || sendMutation.isPending}
                      aria-label="Send message"
                      className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent-500 text-white hover:bg-accent-600 active:scale-95 transition-all disabled:opacity-40 flex-shrink-0"
                    >
                      <Send className="h-4 w-4" aria-hidden="true" />
                    </button>
                  </form>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </PageContainer>
  )
}
