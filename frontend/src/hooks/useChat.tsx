import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
  useRef,
  type ReactNode,
} from 'react'
import { useQuery, useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import { api } from '../api/client'
import type { ChatTurn, AgentResponse, Conversation } from '../types'
import { useSession } from '../utils/identity'

/**
 * The single source of truth for the Campus AI conversation.
 *
 * Both the minimized floating widget and the full-page chat render from this
 * context, so a question asked in one shows up in the other — they are two
 * views of one conversation, not two separate chats.
 *
 * Threads: each conversation is identified by a `conversationId` string. The
 * active thread is held here; switching threads refetches that thread's turns.
 * A brand-new chat gets a fresh id and starts empty (it is not persisted until
 * the first turn is saved).
 *
 * This talks to the real backend:
 *   - POST /api/agent             → the agent answer (tools + RAG fallback)
 *   - GET  /ask/history           → turns (all, or one conversation)
 *   - GET  /ask/conversations     → the thread list
 *   - DELETE /ask/history         → clear a thread (or all)
 * The endpoint is scoped to the acting user by the JWT, so there is no userId
 * parameter to pass.
 */

interface ChatContextValue {
  /** Turns for the active conversation, oldest-first. */
  history: ChatTurn[]
  historyLoading: boolean
  historyCount: number
  /** The question being asked right now, shown as a pending bubble. */
  activeAsk: string | null
  /** The answer for the current ask, shown before the history refetch lands. */
  liveResponse: AgentResponse | null
  question: string
  setQuestion: (value: string) => void
  ask: (q: string) => void
  isPending: boolean
  error: Error | null
  sendMutation: UseMutationResult<AgentResponse, Error, string, unknown>
  clear: () => void
  clearPending: boolean
  /** True when there is nothing to show yet — used for the empty state. */
  isEmpty: boolean
  // --- threads ---
  /** All threads for the signed-in user, newest first. */
  conversations: Conversation[]
  /** The currently open thread id. */
  conversationId: string
  /** Switch to an existing thread. */
  selectConversation: (id: string) => void
  /** Start a fresh, empty thread (not persisted until the first turn). */
  newConversation: () => void
  /** Delete a thread and, if it was active, drop back to a new thread. */
  deleteConversation: (id: string) => void
}

const ChatContext = createContext<ChatContextValue | null>(null)

/** Generate a fresh, reasonably-unique conversation id. */
function newConversationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `c-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function ChatProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  // Reactive, not a one-shot read: this provider sits above the router and
  // never unmounts, so a snapshot taken at mount would keep the first account
  // forever. `account` is what re-keys every query and re-runs the reset below.
  const account = useSession()?.userId ?? null
  const [question, setQuestion] = useState('')
  const [activeAsk, setActiveAsk] = useState<string | null>(null)
  const [liveResponse, setLiveResponse] = useState<AgentResponse | null>(null)
  const [conversationId, setConversationId] = useState<string>(() => newConversationId())
  const lastQuestionRef = useRef<string | null>(null)

  /**
   * A new account starts a new conversation.
   *
   * These four pieces of state live in the provider, not in the query cache,
   * so clearing the cache does not touch them. Without this, the answer the
   * previous user was reading stayed on screen after a switch — the query keys
   * changed correctly and fetched the new user's (empty) history, but the live
   * bubble was still the old account's reply. This is that leak, closed.
   */
  useEffect(() => {
    setLiveResponse(null)
    setActiveAsk(null)
    setQuestion('')
    lastQuestionRef.current = null
    setConversationId(newConversationId())
  }, [account])

  const { data: history = [], isLoading: historyLoading } = useQuery({
    queryKey: ['chat-history', account, conversationId],
    queryFn: () =>
      api.get<ChatTurn[]>(`/ask/history?limit=100&conversationId=${encodeURIComponent(conversationId)}`),
    // A brand-new thread has no persisted turns; fetching is harmless but pointless.
    enabled: !!account,
  })

  const { data: historyCount = 0 } = useQuery({
    queryKey: ['chat-history-count', account],
    queryFn: async () => (await api.get<{ count: number }>('/ask/history/count')).count,
    enabled: !!account,
  })

  const { data: conversations = [] } = useQuery({
    queryKey: ['chat-conversations', account],
    queryFn: () => api.get<Conversation[]>('/ask/conversations'),
    enabled: !!account,
  })

  const sendMutation = useMutation({
    mutationFn: (q: string) =>
      api.post<AgentResponse>('/api/agent', { question: q, conversationId }),
    onSuccess: (resp) => {
      // Persist the live answer locally so it renders immediately — the
      // history refetch from the backend may lag by a few milliseconds.
      setLiveResponse(resp)
      queryClient.invalidateQueries({ queryKey: ['chat-history', account] })
      queryClient.invalidateQueries({ queryKey: ['chat-history-count', account] })
      queryClient.invalidateQueries({ queryKey: ['chat-conversations', account] })
    },
  })

  const clearMutation = useMutation({
    mutationFn: () =>
      api.delete<{ cleared: number }>(`/ask/history?conversationId=${encodeURIComponent(conversationId)}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat-history', account] })
      queryClient.invalidateQueries({ queryKey: ['chat-history-count', account] })
      queryClient.invalidateQueries({ queryKey: ['chat-conversations', account] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) =>
      api.delete<{ cleared: number }>(`/ask/history?conversationId=${encodeURIComponent(id)}`),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ['chat-history', account] })
      queryClient.invalidateQueries({ queryKey: ['chat-history-count', account] })
      queryClient.invalidateQueries({ queryKey: ['chat-conversations', account] })
      // If the thread we just deleted was the open one, drop to a fresh thread.
      if (id === conversationId) {
        setConversationId(newConversationId())
        setLiveResponse(null)
      }
    },
  })

  // Once the refetch includes the turn we just answered, drop the live copy so
  // the turn doesn't render twice (once from liveResponse, once from history).
  useEffect(() => {
    if (!liveResponse || history.length === 0 || sendMutation.isPending) return
    const last = history[0]
    if (last && last.question === lastQuestionRef.current) {
      setLiveResponse(null)
    }
  }, [history, liveResponse, sendMutation.isPending])

  const ask = useCallback(
    (q: string) => {
      const trimmed = q.trim()
      if (!trimmed || sendMutation.isPending) return
      // Remember the question so we can tell when history catches up.
      lastQuestionRef.current = trimmed
      setLiveResponse(null)
      setActiveAsk(trimmed)
      sendMutation.mutate(trimmed, {
        onSuccess: () => {
          setActiveAsk(null)
          setQuestion('') // clear the composer only once the answer arrives
        },
        onSettled: (resp) => {
          if (!resp) return
          if (resp.type === 'clarification') {
            setActiveAsk(resp.text)
          } else if (resp.type === 'error') {
            setActiveAsk(`Error: ${resp.text}`)
          }
        },
      })
    },
    [sendMutation],
  )

  const clear = useCallback(() => {
    clearMutation.mutate()
  }, [clearMutation])

  const selectConversation = useCallback((id: string) => {
    setConversationId(id)
    setLiveResponse(null)
    setActiveAsk(null)
    lastQuestionRef.current = null
  }, [])

  const newConversation = useCallback(() => {
    setConversationId(newConversationId())
    setLiveResponse(null)
    setActiveAsk(null)
    lastQuestionRef.current = null
  }, [])

  const deleteConversation = useCallback(
    (id: string) => {
      deleteMutation.mutate(id)
    },
    [deleteMutation],
  )

  const isPending = sendMutation.isPending
  const isEmpty = history.length === 0 && !liveResponse && !isPending && !activeAsk

  const value = useMemo<ChatContextValue>(
    () => ({
      history,
      historyLoading,
      historyCount,
      activeAsk,
      liveResponse,
      question,
      setQuestion,
      ask,
      isPending,
      error: (sendMutation.error as Error | null) ?? null,
      sendMutation,
      clear,
      clearPending: clearMutation.isPending,
      isEmpty,
      conversations,
      conversationId,
      selectConversation,
      newConversation,
      deleteConversation,
    }),
    [
      history,
      historyLoading,
      historyCount,
      activeAsk,
      liveResponse,
      question,
      ask,
      isPending,
      sendMutation,
      clear,
      clearMutation.isPending,
      isEmpty,
      conversations,
      conversationId,
      selectConversation,
      newConversation,
      deleteConversation,
    ],
  )

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

export function useChat(): ChatContextValue {
  const ctx = useContext(ChatContext)
  if (!ctx) throw new Error('useChat must be used within a <ChatProvider>')
  return ctx
}
