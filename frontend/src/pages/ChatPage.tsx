import { useState, useEffect, useRef, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Student, ChatTurn, ChatSource, AgentResponse } from '../types'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { CitedAnswer } from '../components/CitedAnswer'
import { cn } from '../utils/cn'
import {
  Send,
  Bot,
  Loader2,
  ExternalLink,
  History,
  Trash2,
  X,
  Sparkles,
  AlertTriangle,
  ArrowUpRight,
  BookOpen,
} from 'lucide-react'

const SUGGESTED = [
  { q: 'How many students can be in a hackathon team?', hint: 'Teams & rules' },
  { q: 'When is the submission deadline?', hint: 'Deadlines' },
  { q: 'How complete is my profile?', hint: 'Profile' },
  { q: 'Recommend skills for me', hint: 'Growth' },
  { q: 'Open postings', hint: 'Careers' },
  { q: 'Visible documents', hint: 'Library' },
]

function formatTime(dateString: string) {
  try {
    return new Date(dateString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

function Sources({ sources, idPrefix }: { sources?: ChatSource[]; idPrefix: string }) {
  if (!sources || sources.length === 0) return null
  return (
    <div className="flex items-center gap-1.5 flex-wrap pt-2.5 mt-1 border-t border-campus-900/[0.07]">
      <span className="inline-flex items-center gap-1 text-micro font-medium text-surface-400">
        <BookOpen className="h-3.5 w-3.5" aria-hidden="true" /> Grounded in
      </span>
      {sources.map((s, i) => (
        <span key={i} id={`${idPrefix}-source-${i + 1}`} className="inline-flex items-center gap-1 rounded-full bg-campus-50 border border-campus-100 px-2.5 py-1 text-micro font-medium text-campus-700 scroll-mt-2">
          <ExternalLink className="h-3 w-3" aria-hidden="true" />
          {s.documentTitle}{s.section && <span className="text-campus-400">· {s.section}</span>}{s.pageNumber != null && <span className="text-campus-400">· p.{s.pageNumber}</span>}
        </span>
      ))}
    </div>
  )
}

export default function ChatPage() {
  const queryClient = useQueryClient()
  const [question, setQuestion] = useState('')
  const [showHistory, setShowHistory] = useState(false)
  const [demoUser, setDemoUser] = useState<string>('')
  const [activeAsk, setActiveAsk] = useState<string | null>(null)
  const [liveResponse, setLiveResponse] = useState<AgentResponse | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const { data: users = [] } = useQuery({
    queryKey: ['demo-users'],
    queryFn: () => api.get<Student[]>('/demo/users'),
  })

  const { data: history = [], isLoading: historyLoading } = useQuery({
    queryKey: ['chat-history', demoUser],
    queryFn: () => api.get<ChatTurn[]>('/ask/history?limit=50'),
    enabled: !!demoUser,
  })

  const { data: historyCount = 0 } = useQuery({
    queryKey: ['chat-history-count', demoUser],
    queryFn: () => api.get<number>('/ask/history/count'),
    enabled: !!demoUser,
  })

  const sendMutation = useMutation({
    mutationFn: (q: string) => api.post<AgentResponse>('/api/agent', { question: q }),
  })

  const clearMutation = useMutation({
    mutationFn: () => api.delete<{ cleared: number }>('/ask/history'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat-history', demoUser] })
      queryClient.invalidateQueries({ queryKey: ['chat-history-count', demoUser] })
    },
  })

  useEffect(() => {
    const saved = localStorage.getItem('demoUserId')
    if (saved) setDemoUser(saved)
  }, [])

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [history.length, liveResponse, sendMutation.isPending, scrollToBottom])

  const ask = (q: string) => {
    const trimmed = q.trim()
    if (!trimmed || sendMutation.isPending) return
    // Clear any previous live response so it doesn't repeat on the next ask
    setLiveResponse(null)
    // Show the user's question as a chat bubble while waiting for the response
    setActiveAsk(trimmed)
    // Keep the query visible in the input until the response arrives,
    // then clear it so the user sees the answer take its place.
    sendMutation.mutate(trimmed, {
      onSuccess: (resp) => {
        setActiveAsk(null)
        setQuestion('') // clear input only after response is received
        setLiveResponse(resp)
        queryClient.invalidateQueries({ queryKey: ['chat-history', demoUser] })
        queryClient.invalidateQueries({ queryKey: ['chat-history-count', demoUser] })
      },
      onSettled: (resp: AgentResponse | undefined) => {
        if (!resp) return;
        // Handle agent response — display answer as a chat turn
        if (resp.type === 'clarification') {
          setActiveAsk(resp.text)
        } else if (resp.type === 'error') {
          setActiveAsk(`Error: ${resp.text}`)
        }
      },
    })
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    ask(question)
  }

  const handleClearHistory = async () => {
    if (!confirm('Clear all chat history for this user?')) return
    await clearMutation.mutateAsync()
  }

  const isEmpty = history.length === 0 && !liveResponse && !sendMutation.isPending && !activeAsk

  return (
    <div className="mx-auto max-w-3xl">
      {/* Top strip */}
      <div className="flex items-center gap-3 mb-5">
        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#667eea] to-[#764ba2] shadow-card flex-shrink-0">
          <Bot className="h-5 w-5 text-accent-300" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-display font-bold text-[19px] text-campus-950 tracking-tight leading-tight">Ask Campus AI</p>
          <p className="text-caption text-surface-500 truncate">Answers grounded in your college knowledge base — never invented</p>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <div className="relative hidden sm:block">
            <select
              value={demoUser}
              onChange={(e) => {
                const val = e.target.value
                setDemoUser(val)
                localStorage.setItem('demoUserId', val)
              }}
              aria-label="Ask as user"
              className="appearance-none bg-white border border-campus-900/10 rounded-xl pl-3 pr-8 py-2 text-body-sm font-medium text-campus-900 focus:outline-none focus:ring-2 focus:ring-campus-500 cursor-pointer shadow-card max-w-[190px]"
            >
              <option value="">Ask as…</option>
              {users.map((u) => (
                <option key={u.id} value={String(u.id)}>
                  {u.name} · {u.branch}
                </option>
              ))}
            </select>
          </div>
          <Button variant="outline" size="sm" onClick={() => setShowHistory(!showHistory)} aria-expanded={showHistory}>
            <History className="h-4 w-4" />
            <span className="hidden sm:inline">History</span>
            {historyCount > 0 && (
              <span className="ml-0.5 min-w-[20px] px-1.5 py-0.5 text-micro font-bold text-white bg-gradient-to-br from-[#667eea] to-[#764ba2] rounded-full tabular-nums">
                {historyCount}
              </span>
            )}
          </Button>
        </div>
      </div>

      {/* History drawer */}
      {showHistory && (
        <div className="mb-5 rounded-2xl border border-campus-900/[0.08] bg-white shadow-card overflow-hidden animate-slide-down">
          <div className="flex items-center justify-between px-4 py-3 border-b border-campus-900/[0.06] bg-campus-50/60">
            <p className="font-display font-semibold text-body-sm text-campus-950">Recent conversations</p>
            <div className="flex items-center gap-1.5">
              <Button variant="ghost" size="sm" onClick={handleClearHistory} aria-label="Clear history">
                <Trash2 className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setShowHistory(false)} aria-label="Close history">
                <X className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <div className="max-h-56 overflow-y-auto p-2 scrollbar-light">
            {historyLoading ? (
              <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-campus-500" /></div>
            ) : history.length === 0 ? (
              <p className="text-center text-body-sm text-surface-400 py-6">No conversations yet — your questions will appear here.</p>
            ) : (
              history.slice().reverse().map((turn) => (
                <button
                  key={turn.id}
                  onClick={() => { ask(turn.question) }}
                  className="w-full text-left rounded-xl px-3 py-2.5 hover:bg-campus-50 transition-colors focus:outline-none focus:ring-2 focus:ring-campus-500"
                >
                  <p className="text-body-sm font-medium text-campus-950 truncate">{turn.question}</p>
                  <p className="text-caption text-surface-400 truncate mt-0.5">{turn.answer}</p>
                </button>
              ))
            )}
          </div>
        </div>
      )}

      {/* Welcome */}
      {isEmpty && (
        <div className="animate-fade-in mb-6">
          <div className="relative overflow-hidden rounded-3xl text-white p-7 sm:p-9 bg-gradient-to-br from-[#667eea] via-[#764ba2] to-[#f093fb] shadow-elevated">
            <div className="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl" aria-hidden="true" />
            <div className="absolute -bottom-24 -left-16 h-56 w-56 rounded-full bg-white/[0.08] blur-3xl" aria-hidden="true" />
            <div className="relative">
              <p className="inline-flex items-center gap-1.5 rounded-full bg-white/10 border border-white/10 px-3 py-1 text-micro font-medium text-campus-100">
                <Sparkles className="h-3.5 w-3.5 text-accent-300" aria-hidden="true" />
                Grounded in campus documents
              </p>
              <h2 className="font-display font-bold tracking-tight text-[30px] sm:text-[36px] leading-[1.08] mt-4">
                What do you want<br />to know today?
              </h2>
              <p className="text-body-sm text-campus-100/70 mt-2.5 max-w-[46ch]">
                Projects, deadlines, documents, mentors, roles — ask in plain words and get answers with sources.
              </p>
            </div>
          </div>
          <div className="grid sm:grid-cols-2 gap-2.5 mt-3">
            {SUGGESTED.map(s => (
              <button
                key={s.q}
                onClick={() => ask(s.q)}
                className="group flex items-center gap-3 rounded-2xl border border-campus-900/[0.08] bg-white px-4 py-3.5 text-left shadow-card hover:shadow-card-hover hover:-translate-y-[1px] hover:border-campus-900/15 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-campus-500"
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-body-sm font-semibold text-campus-950 truncate">{s.q}</span>
                  <span className="block text-caption text-surface-400 mt-0.5">{s.hint}</span>
                </span>
                <ArrowUpRight className="h-4 w-4 text-surface-300 group-hover:text-accent-500 group-hover:translate-x-[1px] group-hover:-translate-y-[1px] transition-all flex-shrink-0" aria-hidden="true" />
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Thread */}
      {!isEmpty && (
        <div className="flex flex-col gap-5" role="log" aria-live="polite" aria-label="Chat messages">
          {history.slice().reverse().map((turn) => (
            <div key={turn.id} className="animate-slide-up">
              <div className="flex justify-end mb-2.5">
                <p className="max-w-[85%] rounded-2xl rounded-br-md bg-gradient-to-br from-[#667eea] to-[#764ba2] px-4 py-2.5 text-body-sm font-medium text-white shadow-card">
                  {turn.question}
                </p>
              </div>
              <div className={cn(
                'rounded-2xl rounded-bl-md border px-5 py-4 shadow-card',
                turn.answered ? 'bg-white border-campus-900/[0.07]' : 'bg-amber-50/80 border-amber-200/70'
              )}>
                <div className="flex items-center gap-2 mb-2">
                  <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-gradient-to-br from-[#667eea] to-[#764ba2] flex-shrink-0" aria-hidden="true">
                    <Bot className="h-3.5 w-3.5 text-accent-300" />
                  </span>
                  <span className="text-micro font-semibold text-surface-400">
                    {turn.answered ? 'Campus AI' : 'Outside campus knowledge'} · {formatTime(turn.createdAt)}
                  </span>
                </div>
                <CitedAnswer text={turn.answer ?? ''} sources={turn.sources} idPrefix={`turn-${turn.id}`} />
                <Sources sources={turn.sources} idPrefix={`turn-${turn.id}`} />
              </div>
            </div>
          ))}

          {(activeAsk || sendMutation.isPending) && activeAsk && (
            <div className="animate-slide-up">
              <div className="flex justify-end mb-2.5">
                <p className="max-w-[85%] rounded-2xl rounded-br-md bg-gradient-to-br from-[#667eea] to-[#764ba2] px-4 py-2.5 text-body-sm font-medium text-white shadow-card">{activeAsk}</p>
              </div>
              <div className="rounded-2xl rounded-bl-md border border-dashed border-campus-900/15 bg-white/70 px-5 py-4">
                <p className="flex items-center gap-2.5 text-body-sm text-surface-500">
                  <Loader2 className="h-4 w-4 animate-spin text-campus-500" aria-hidden="true" />
                  Searching campus knowledge…
                </p>
              </div>
            </div>
          )}

          {sendMutation.isError && (
            <div className="rounded-2xl border border-red-200 bg-red-50/70 px-5 py-4 flex items-start gap-3 animate-slide-up" role="alert">
              <AlertTriangle className="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" aria-hidden="true" />
              <div>
                <p className="font-semibold text-body-sm text-red-900">Couldn't reach the backend</p>
                <p className="text-body-sm text-red-700/80 mt-0.5">{sendMutation.error?.message}</p>
              </div>
            </div>
          )}

          {liveResponse && !sendMutation.isPending && (
            <div className="animate-slide-up" key="live-answer">
              <div className={cn(
                'rounded-2xl rounded-bl-md border px-5 py-4 shadow-card',
                'bg-white border-campus-900/[0.07]'
              )}>
                <div className="flex items-center gap-2 mb-2">
                  <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-gradient-to-br from-[#667eeb] to-[#764ba2] flex-shrink-0" aria-hidden="true">
                    <Bot className="h-3.5 w-3.5 text-accent-300" />
                  </span>
                  <span className="text-micro font-semibold text-surface-400">
                    Campus AI · just now
                  </span>
                </div>
                <CitedAnswer text={liveResponse.text ?? ''} sources={liveResponse.data?.sources as any} idPrefix="live-answer" />
                <Sources sources={liveResponse.data?.sources as any} idPrefix="live-answer" />
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>
      )}

      {/* Composer */}
      <div className="sticky bottom-4 mt-6">
        <form
          onSubmit={handleSubmit}
          className="flex items-center gap-2 rounded-2xl border border-campus-900/10 bg-white p-2 pl-5 shadow-elevated focus-within:ring-2 focus-within:ring-campus-500 focus-within:border-transparent transition-shadow"
        >
          <input
            ref={inputRef}
            type="text"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Ask about projects, deadlines, documents…"
            className="flex-1 min-w-0 bg-transparent text-body placeholder:text-surface-400 focus:outline-none disabled:opacity-60"
            disabled={sendMutation.isPending}
            aria-label="Ask a question"
          />
          <button
            type="submit"
            disabled={!question.trim() || sendMutation.isPending}
            aria-label="Send question"
            className="flex h-11 w-11 items-center justify-center rounded-xl bg-accent-500 text-white shadow-card hover:bg-accent-600 active:scale-95 transition-all disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
          >
            {sendMutation.isPending ? (
              <Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />
            ) : (
              <Send className="h-5 w-5" aria-hidden="true" />
            )}
          </button>
        </form>
        {!demoUser && (
          <p className="text-center text-caption text-surface-400 mt-2">
            Pick a demo user above — answers are personalized to you. <Badge variant="outline" size="sm" className="ml-1">Demo mode</Badge>
          </p>
        )}
      </div>
    </div>
  )
}
