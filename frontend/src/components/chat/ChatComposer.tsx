import { Send, Loader2 } from 'lucide-react'
import { cn } from '../../utils/cn'

/**
 * Shared message input. `compact` renders the smaller variant used by the
 * floating widget; the full page uses the larger, sticky composer.
 */
export function ChatComposer({
  value,
  onChange,
  onSubmit,
  disabled,
  loading,
  placeholder = 'Ask about projects, deadlines, documents…',
  autoFocus = false,
  compact = false,
}: {
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  disabled?: boolean
  loading?: boolean
  placeholder?: string
  autoFocus?: boolean
  compact?: boolean
}) {
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit()
      }}
      className={cn(
        'flex items-center gap-2 border border-campus-900/10 bg-white shadow-elevated transition-shadow',
        'focus-within:ring-2 focus-within:ring-campus-500 focus-within:border-transparent',
        compact ? 'rounded-xl p-1.5 pl-3.5' : 'rounded-2xl p-2 pl-5',
      )}
    >
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        autoFocus={autoFocus}
        disabled={disabled}
        aria-label="Ask a question"
        className={cn(
          'flex-1 min-w-0 bg-transparent text-body placeholder:text-surface-400 focus:outline-none disabled:opacity-60',
        )}
      />
      <button
        type="submit"
        disabled={!value.trim() || disabled || loading}
        aria-label="Send question"
        className={cn(
          'flex items-center justify-center rounded-xl bg-accent-500 text-white shadow-card hover:bg-accent-600 active:scale-95 transition-all disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0',
          compact ? 'h-9 w-9' : 'h-11 w-11',
        )}
      >
        {loading ? (
          <Loader2 className={cn('animate-spin', compact ? 'h-4 w-4' : 'h-5 w-5')} aria-hidden="true" />
        ) : (
          <Send className={cn(compact ? 'h-4 w-4' : 'h-5 w-5')} aria-hidden="true" />
        )}
      </button>
    </form>
  )
}
