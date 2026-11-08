import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader } from '../components/ui/PageState'
import { useChat } from '../hooks/useChat'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { ChatThread } from '../components/chat/ChatThread'
import { ChatComposer } from '../components/chat/ChatComposer'
import { useNavigate } from 'react-router-dom'
import { cn } from '../utils/cn'
import {
  History,
  Trash2,
  X,
  Plus,
  Sparkles,
  ArrowUpRight,
  ArrowLeft,
} from 'lucide-react'

const SUGGESTED = [
  { q: 'How many students can be in a hackathon team?', hint: 'Teams & rules' },
  { q: 'When is the submission deadline?', hint: 'Deadlines' },
  { q: 'How complete is my profile?', hint: 'Profile' },
  { q: 'Recommend skills for me', hint: 'Growth' },
  { q: 'Open postings', hint: 'Careers' },
  { q: 'Visible documents', hint: 'Library' },
]

/** Relative-ish label for a thread's last activity. */
function formatThreadTime(iso: string): string {
  try {
    const then = new Date(iso).getTime()
    const mins = Math.floor((Date.now() - then) / 60000)
    if (mins < 1) return 'just now'
    if (mins < 60) return `${mins}m ago`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours}h ago`
    const days = Math.floor(hours / 24)
    if (days < 7) return `${days}d ago`
    return new Date(iso).toLocaleDateString()
  } catch {
    return ''
  }
}

/**
 * The full Campus AI chat.
 *
 * Renders inside the app shell on `/chat` (reached from the sidebar), and as a
 * standalone full-screen page on `/chat/full` (reached by expanding the floating
 * widget). Both share the same conversation via {@link useChat}, so switching
 * between the mini widget and this page continues the same thread.
 */
export default function ChatPage({ fullPage = false }: { fullPage?: boolean }) {
  const navigate = useNavigate()
  const chat = useChat()
  const [showHistory, setShowHistory] = useState(false)
  const [confirmClear, setConfirmClear] = useState(false)

  const body = (
    <>
      {/* Toolbar: thread picker toggle + new chat. */}
      <div className="flex items-center gap-2 mb-5 flex-wrap">
        <Button variant="outline" size="sm" onClick={() => setShowHistory(!showHistory)} aria-expanded={showHistory}>
          <History className="h-4 w-4" />
          Conversations
          {chat.conversations.length > 0 && (
            <span className="ml-0.5 min-w-[20px] px-1.5 py-0.5 text-micro font-bold text-white bg-gradient-to-br from-[#667eea] to-[#764ba2] rounded-full tabular-nums">
              {chat.conversations.length}
            </span>
          )}
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            chat.newConversation()
            setShowHistory(false)
          }}
        >
          <Plus className="h-4 w-4" />
          New chat
        </Button>
      </div>

      {/* Conversation (thread) picker */}
      {showHistory && (
        <div className="mb-5 rounded-2xl border border-campus-900/[0.08] bg-white shadow-card overflow-hidden animate-slide-down">
          <div className="flex items-center justify-between px-4 py-3 border-b border-campus-900/[0.06] bg-campus-50/60">
            <p className="font-display font-semibold text-body-sm text-campus-950">Your conversations</p>
            <div className="flex items-center gap-1.5">
              <Button variant="ghost" size="sm" onClick={() => setConfirmClear(true)} aria-label="Clear this conversation">
                <Trash2 className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setShowHistory(false)} aria-label="Close conversations">
                <X className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <div className="max-h-64 overflow-y-auto p-2 scrollbar-light">
            {chat.conversations.length === 0 ? (
              <p className="text-center text-body-sm text-surface-400 py-6">
                No conversations yet — your threads will appear here.
              </p>
            ) : (
              chat.conversations.map((c) => (
                <div
                  key={c.id || 'default'}
                  className={cn(
                    'group flex items-center gap-2 rounded-xl px-2 py-1.5 transition-colors',
                    c.id === chat.conversationId ? 'bg-campus-50' : 'hover:bg-campus-50/70',
                  )}
                >
                  <button
                    onClick={() => {
                      chat.selectConversation(c.id)
                      setShowHistory(false)
                    }}
                    className="min-w-0 flex-1 text-left rounded-lg px-1.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-campus-500"
                  >
                    <p className="text-body-sm font-medium text-campus-950 truncate">
                      {c.title || 'Untitled conversation'}
                    </p>
                    <p className="text-caption text-surface-400 truncate mt-0.5">
                      {c.turnCount} {c.turnCount === 1 ? 'message' : 'messages'}
                      {c.lastMessageAt && ` · ${formatThreadTime(c.lastMessageAt)}`}
                    </p>
                  </button>
                  <button
                    onClick={() => chat.deleteConversation(c.id)}
                    className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg text-surface-300 opacity-0 group-hover:opacity-100 hover:bg-red-50 hover:text-red-500 transition-all focus:outline-none focus:ring-2 focus:ring-red-400"
                    aria-label={`Delete conversation: ${c.title || 'Untitled conversation'}`}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Welcome / empty state */}
      {chat.isEmpty && (
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
                What do you want
                <br />
                to know today?
              </h2>
              <p className="text-body-sm text-campus-100/70 mt-2.5 max-w-[46ch]">
                Projects, deadlines, documents, mentors, roles — ask in plain words and get answers with sources.
              </p>
            </div>
          </div>
          <div className="grid sm:grid-cols-2 gap-2.5 mt-3">
            {SUGGESTED.map((s) => (
              <button
                key={s.q}
                onClick={() => chat.ask(s.q)}
                className="group flex items-center gap-3 rounded-2xl border border-campus-900/[0.08] bg-white px-4 py-3.5 text-left shadow-card hover:shadow-card-hover hover:-translate-y-[1px] hover:border-campus-900/15 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-campus-500"
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-body-sm font-semibold text-campus-950 truncate">{s.q}</span>
                  <span className="block text-caption text-surface-400 mt-0.5">{s.hint}</span>
                </span>
                <ArrowUpRight
                  className="h-4 w-4 text-surface-300 group-hover:text-accent-500 group-hover:translate-x-[1px] group-hover:-translate-y-[1px] transition-all flex-shrink-0"
                  aria-hidden="true"
                />
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Thread */}
      {!chat.isEmpty && (
        <ChatThread
          history={chat.history}
          activeAsk={chat.activeAsk}
          isPending={chat.isPending}
          error={chat.error}
          liveResponse={chat.liveResponse}
        />
      )}

      {/* Composer */}
      <div className="sticky bottom-4 mt-6">
        <ChatComposer
          value={chat.question}
          onChange={chat.setQuestion}
          onSubmit={() => chat.ask(chat.question)}
          disabled={chat.isPending}
          loading={chat.isPending}
        />
      </div>

      <ConfirmDialog
        open={confirmClear}
        title="Delete this conversation?"
        body="Every question and answer in this thread is deleted. Other conversations and the knowledge base are untouched."
        confirmLabel="Delete conversation"
        pending={chat.clearPending}
        onCancel={() => setConfirmClear(false)}
        onConfirm={() => {
          chat.clear()
          setConfirmClear(false)
        }}
      />
    </>
  )

  // Standalone full-screen variant: its own header + back-to-app control, no
  // surrounding sidebar. Used by /chat/full.
  if (fullPage) {
    return (
      <div className="min-h-screen bg-surface-50">
        <header className="sticky top-0 z-10 border-b border-campus-900/[0.07] bg-white/85 backdrop-blur">
          <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-3">
            <button
              onClick={() => navigate(-1)}
              className="flex h-9 w-9 items-center justify-center rounded-xl text-surface-500 hover:bg-surface-100 hover:text-campus-700 transition-colors"
              aria-label="Back"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <div className="flex items-center gap-2.5 min-w-0">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-[#667eea] to-[#764ba2] flex-shrink-0">
                <Sparkles className="h-5 w-5 text-white" aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <p className="font-display font-bold text-body-sm text-campus-950 leading-tight">Campus AI</p>
                <p className="text-caption text-surface-400 leading-tight">Grounded in campus documents</p>
              </div>
            </div>
            <div className="ml-auto">
              <Badge variant="success">
                <span className="mr-1 inline-block h-1.5 w-1.5 rounded-full bg-emerald-500" aria-hidden="true" />
                Grounded
              </Badge>
            </div>
          </div>
        </header>
        <main className="mx-auto max-w-3xl px-4 py-6">
          <div className="pb-40">{body}</div>
        </main>
      </div>
    )
  }

  return (
    <PageContainer width="reading">
      <PageHeader
        title="Ask Campus AI"
        sub="Answers grounded in your college knowledge base — never invented"
        actions={
          <Badge variant="success">
            <span className="mr-1 inline-block h-1.5 w-1.5 rounded-full bg-emerald-500" aria-hidden="true" />
            Grounded
          </Badge>
        }
      />
      {body}
    </PageContainer>
  )
}
