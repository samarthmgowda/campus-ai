import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { api } from '../api/client'
import { Bot, X, Send, Plus } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { cn } from '../utils/cn'

interface Message {
  id: number
  text: string
  sender: 'user' | 'bot'
  timestamp: string
}

interface Thread {
  id: string
  title: string
  lastMessage: string
  updatedAt: string
}

export default function ChatWidget() {
  const [isOpen, setIsOpen] = useState(false)
  const [currentThreadId, setCurrentThreadId] = useState<string | null>(null)
  const [input, setInput] = useState('')
  const [isTyping, setIsTyping] = useState(false)

  const { data: threads } = useQuery({
    queryKey: ['chat-threads'],
    queryFn: () => api.get<Thread[]>('/chat/threads')
  })

  const { data: messages } = useQuery({
    queryKey: ['chat-messages', currentThreadId],
    queryFn: () => api.get<Message[]>(currentThreadId ? `/chat/threads/${currentThreadId}/messages` : '/chat/messages'),
    enabled: isOpen
  })

  const sendMutation = useMutation({
    mutationFn: (text: string) => api.post('/chat/messages', { text, threadId: currentThreadId }),
    onSuccess: () => {
      setInput('')
    }
  })

  const handleSend = async () => {
    if (!input.trim()) return
    setIsTyping(true)
    try {
      await sendMutation.mutateAsync(input)
    } finally {
      setIsTyping(false)
    }
  }

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end gap-4">
      {isOpen && (
        <div className="w-[380px] h-[550px] max-h-[calc(100vh-120px)] bg-white rounded-3xl shadow-2xl border border-campus-900/[0.08] flex flex-col overflow-hidden animate-slide-up">
          <div className="h-16 px-5 flex items-center justify-between bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white">
            <div className="flex items-center gap-2.5">
              <Bot className="h-5 w-5" />
              <p className="font-display font-bold text-body-sm">Campus AI Assistant</p>
            </div>
            <button onClick={() => setIsOpen(false)} className="p-1 hover:bg-white/20 rounded-lg transition-colors">
              <X className="h-5 w-5" />
            </button>
          </div>

          <div className="flex-1 overflow-hidden flex">
            {/* Threads Sidebar */}
            <div className="w-24 sm:w-32 border-r border-campus-900/[0.05] bg-surface-50/50 flex flex-col">
              <button
                onClick={() => setCurrentThreadId(null)}
                className="p-3 text-center hover:bg-surface-100 transition-colors border-b border-campus-900/[0.05]"
              >
                <Plus className="h-5 w-5 mx-auto text-surface-400" />
                <span className="text-micro block mt-1 text-surface-500 font-medium">New</span>
              </button>
              <div className="flex-1 overflow-y-auto p-2 space-y-1">
                {threads?.map(t => (
                  <button
                    key={t.id}
                    onClick={() => setCurrentThreadId(t.id)}
                    className={cn(
                      "w-full text-left p-2 rounded-xl text-micro transition-all truncate",
                      currentThreadId === t.id ? "bg-white shadow-sm text-campus-700 font-semibold" : "text-surface-500 hover:bg-surface-100"
                    )}
                  >
                    {t.title}
                  </button>
                ))}
              </div>
            </div>

            {/* Chat Area */}
            <div className="flex-1 flex flex-col bg-white">
              <div className="flex-1 overflow-y-auto p-4 space-y-4 scrollbar-light">
                {messages?.map(m => (
                  <div key={m.id} className={cn("flex", m.sender === 'user' ? "justify-end" : "justify-start")}>
                    <div className={cn(
                      "max-w-[80%] p-3 rounded-2xl text-body-sm",
                      m.sender === 'user'
                        ? "bg-campus-600 text-white rounded-tr-none"
                        : "bg-surface-100 text-surface-800 rounded-tl-none"
                    )}>
                      {m.text}
                    </div>
                  </div>
                ))}
                {isTyping && (
                  <div className="flex justify-start">
                    <div className="bg-surface-100 p-3 rounded-2xl rounded-tl-none flex gap-1">
                      <span className="w-1.5 h-1.5 bg-surface-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                      <span className="w-1.5 h-1.5 bg-surface-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                      <span className="w-1.5 h-1.5 bg-surface-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                    </div>
                  </div>
                )}
              </div>
              <div className="p-4 border-t border-campus-900/[0.05] bg-surface-50/30">
                <div className="flex items-center gap-2">
                  <Input
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSend()}
                    placeholder="Ask anything..."
                    className="bg-white"
                  />
                  <Button variant="accent" size="sm" onClick={handleSend} disabled={sendMutation.isPending || !input.trim()}>
                    <Send className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      <button
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          "h-14 w-14 rounded-full shadow-2xl flex items-center justify-center transition-all duration-300 active:scale-95",
          isOpen ? "bg-white text-campus-600 rotate-90" : "bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white"
        )}
      >
        {isOpen ? <X className="h-6 w-6" /> : <Bot className="h-6 w-6" />}
      </button>
    </div>
  )
}
