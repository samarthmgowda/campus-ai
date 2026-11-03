import type { LucideIcon } from 'lucide-react'
import { Loader2, SearchX, WifiOff } from 'lucide-react'
import { Button } from './Button'
import { cn } from '../../utils/cn'

export function PageHeader({
  title,
  sub,
  actions,
}: {
  title: string
  sub?: string
  actions?: React.ReactNode
}) {
  return (
    <div className="flex items-start justify-between gap-4 flex-wrap mb-6">
      <div className="min-w-0">
        {/* One h1 per route: every authenticated page leads with this header,
            and PageHeader is never rendered twice on a page. */}
        <h1 className="font-display font-bold text-[26px] leading-tight tracking-tight text-campus-950">{title}</h1>
        {sub && <p className="text-body-sm text-surface-500 mt-1 max-w-[60ch]">{sub}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 flex-shrink-0">{actions}</div>}
    </div>
  )
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-3" role="status" aria-label={label}>
      <span className="relative flex h-11 w-11 items-center justify-center">
        <span className="absolute inset-0 rounded-2xl bg-campus-100" aria-hidden="true" />
        <Loader2 className="h-5 w-5 animate-spin text-campus-600" aria-hidden="true" />
      </span>
      <p className="text-body-sm text-surface-500">{label}</p>
    </div>
  )
}

export function SkeletonCard({ className }: { className?: string }) {
  return (
    <div className={cn('rounded-2xl border border-campus-900/[0.06] bg-white p-5', className)} aria-hidden="true">
      <div className="flex items-center gap-3">
        <div className="h-10 w-10 rounded-xl bg-surface-100 animate-shimmer" />
        <div className="flex-1 space-y-2">
          <div className="h-3.5 w-2/3 rounded bg-surface-100 animate-shimmer" />
          <div className="h-3 w-1/3 rounded bg-surface-100 animate-shimmer" />
        </div>
      </div>
      <div className="mt-4 space-y-2">
        <div className="h-3 w-full rounded bg-surface-100 animate-shimmer" />
        <div className="h-3 w-5/6 rounded bg-surface-100 animate-shimmer" />
      </div>
    </div>
  )
}

export function EmptyState({
  icon: Icon,
  title,
  hint,
  action,
}: {
  icon: LucideIcon
  title: string
  hint?: string
  action?: React.ReactNode
}) {
  return (
    <div className="flex flex-col items-center text-center py-14 px-6 rounded-2xl border border-dashed border-campus-900/15 bg-white/60">
      <span className="flex h-13 w-13 h-[52px] w-[52px] items-center justify-center rounded-2xl bg-campus-50 border border-campus-100 mb-4">
        <Icon className="h-6 w-6 text-campus-500" aria-hidden="true" />
      </span>
      <p className="font-display font-semibold text-[17px] text-campus-950">{title}</p>
      {hint && <p className="text-body-sm text-surface-500 mt-1.5 max-w-[44ch]">{hint}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ title, message, onRetry }: { title?: string; message: string; onRetry?: () => void }) {
  const offline = /fetch|network|backend|running/i.test(message)
  return (
    <div className="flex flex-col items-center text-center py-14 px-6 rounded-2xl border border-red-200/70 bg-red-50/50" role="alert">
      <span className="flex h-[52px] w-[52px] items-center justify-center rounded-2xl bg-white border border-red-100 mb-4">
        {offline ? (
          <WifiOff className="h-6 w-6 text-red-400" aria-hidden="true" />
        ) : (
          <SearchX className="h-6 w-6 text-red-400" aria-hidden="true" />
        )}
      </span>
      <p className="font-display font-semibold text-[17px] text-red-900">
        {title || (offline ? 'Backend unreachable' : 'Something went wrong')}
      </p>
      <p className="text-body-sm text-red-700/70 mt-1.5 max-w-[52ch]">{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-5" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}
