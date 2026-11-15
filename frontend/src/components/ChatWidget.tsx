import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bot, X, Maximize2, Sparkles } from 'lucide-react'
import { useChat } from '../hooks/useChat'
import { ChatThread } from './chat/ChatThread'
import { ChatComposer } from './chat/ChatComposer'
import { cn } from '../utils/cn'

const SUGGESTED = ['How complete is my profile?', 'Recommend skills for me', 'Open postings']

/**
 * The minimized Campus AI launcher.
 *
 * A floating bubble opens a compact chat with the same conversation shown on
 * the full page — both read from {@link useChat}. Clicking the "Campus AI"
 * title in the header (or the expand button) opens the full-page chat.
 *
 * The launcher is the one place orange appears in the shell. It is the single
 * persistent call to action on every authenticated page, which is exactly what
 * the 7:4:1 budget reserves orange for — the thing you press. It sits far from
 * the page's own primary button (bottom-right of the viewport versus the header
 * area), so the two never compete.
 */
export default function ChatWidget() {
  const [isOpen, setIsOpen] = useState(false)
  const navigate = useNavigate()
  const chat = useChat()

  const openFullPage = () => {
    setIsOpen(false)
    navigate('/chat/full')
  }

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end gap-4">
      {isOpen && (
        <div className="w-[360px] h-[520px] max-h-[calc(100vh-120px)] bg-er-800 rounded-3xl shadow-elevated border border-white/10 flex flex-col overflow-hidden animate-slide-up">
          <div className="h-14 px-4 flex items-center justify-between bg-gradient-to-br from-er-600 to-er-700 text-white">
            {/* The title is the affordance to expand into the full-page chat. */}
            <button
              onClick={openFullPage}
              className="group flex items-center gap-2.5 min-w-0 rounded-lg -ml-1 px-1 py-1 hover:bg-white/15 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
              title="Open full chat"
            >
              <Bot className="h-5 w-5 flex-shrink-0" />
              <span className="font-display font-bold text-body-sm truncate">Campus AI</span>
              <Maximize2 className="h-3.5 w-3.5 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0" />
            </button>
            <div className="flex items-center gap-1">
              <button
                onClick={openFullPage}
                className="p-1.5 hover:bg-white/20 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
                aria-label="Open full chat"
              >
                <Maximize2 className="h-4 w-4" />
              </button>
              <button
                onClick={() => setIsOpen(false)}
                className="p-1.5 hover:bg-white/20 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
                aria-label="Close chat"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-4 scrollbar-light">
            {chat.isEmpty ? (
              <div className="flex h-full flex-col items-center justify-center text-center px-4">
                <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-er-500 to-er-600 mb-3">
                  <Sparkles className="h-6 w-6 text-white" aria-hidden="true" />
                </span>
                <p className="font-display font-bold text-body text-ink-50">Ask Campus AI</p>
                <p className="text-caption text-ink-400 mt-1 mb-4">
                  Answers grounded in your college knowledge base.
                </p>
                <div className="w-full space-y-2">
                  {SUGGESTED.map((q) => (
                    <button
                      key={q}
                      onClick={() => chat.ask(q)}
                      className="w-full rounded-xl border border-white/10 bg-er-900 px-3 py-2.5 text-left text-caption font-medium text-ink-200 hover:border-er-500/40 hover:bg-er-900/60 hover:text-ink-50 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <ChatThread
                history={chat.history}
                activeAsk={chat.activeAsk}
                isPending={chat.isPending}
                error={chat.error}
                liveResponse={chat.liveResponse}
                compact
              />
            )}
          </div>

          <div className="p-3 border-t border-white/[0.07] bg-er-900/60">
            <ChatComposer
              value={chat.question}
              onChange={chat.setQuestion}
              onSubmit={() => chat.ask(chat.question)}
              disabled={chat.isPending}
              loading={chat.isPending}
              placeholder="Ask anything…"
              compact
            />
            <button
              onClick={openFullPage}
              className={cn(
                'mt-2 w-full text-center text-micro font-medium text-ink-500',
                'hover:text-er-300 transition-colors',
              )}
            >
              Open full chat with history →
            </button>
          </div>
        </div>
      )}

      <button
        onClick={() => setIsOpen(!isOpen)}
        aria-label={isOpen ? 'Close Campus AI' : 'Open Campus AI'}
        className={cn(
          'h-14 w-14 rounded-full flex items-center justify-center transition-all duration-300 active:scale-95',
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-er-950',
          isOpen
            ? 'bg-er-800 text-ink-100 border border-white/12 rotate-90 focus-visible:ring-er-500'
            : 'bg-er-orange text-er-950 shadow-[0_6px_24px_rgba(255,122,26,0.42)] hover:brightness-110 focus-visible:ring-er-orange',
        )}
      >
        {isOpen ? <X className="h-6 w-6" /> : <Bot className="h-6 w-6" />}
      </button>
    </div>
  )
}
