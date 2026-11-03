import { useEffect, useRef, type ReactNode } from 'react'
import { Button } from './Button'

/**
 * ConfirmDialog
 * ─────────────
 * A replacement for `window.confirm()` for destructive actions.
 *
 * `window.confirm` blocks the main thread, cannot be styled, is unlabelled to
 * assistive tech beyond the raw string, and renders differently in every
 * browser. This gives the same guarantee — the action only runs on an explicit
 * confirm — while behaving like the rest of the app.
 *
 * Accessibility contract:
 *   • role="dialog" + aria-modal, labelled by the title and described by the body
 *   • focus moves to the cancel button on open, so Enter does not destroy anything
 *   • Escape and a backdrop click both cancel
 *   • focus returns to whatever was focused before, on close
 *   • while `pending`, none of the three dismissals (Escape, backdrop, cancel
 *     button) do anything — the caller owns the dialog until its mutation settles
 *
 * Deliberately not a general modal: it has no close-on-success, no children slot,
 * and exactly two outcomes. Anything richer should be a page.
 *
 * Dismissal is the caller's job in one respect: this component never closes
 * itself. `open` stays true until the caller flips it, so an async `onConfirm`
 * should close in a `finally`, otherwise the spinner never gets to render.
 */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = true,
  pending = false,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  body?: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  destructive?: boolean
  /** Disables both buttons and spins the confirm one, for async work. */
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const cancelRef = useRef<HTMLButtonElement>(null)
  const restoreRef = useRef<HTMLElement | null>(null)
  // Callers pass inline arrows, so onCancel changes identity every render. Routed
  // through a ref instead of an effect dependency: depending on it directly would
  // re-run the effect on every parent render, which would overwrite the remembered
  // focus target with the dialog's own button and fight the user's focus.
  // Written in an effect (not during render) so it stays React-Compiler clean.
  const onCancelRef = useRef(onCancel)
  useEffect(() => {
    onCancelRef.current = onCancel
  }, [onCancel])

  // While an async confirm is in flight there is no cancel: dismissing would
  // leave the caller's mutation running with no visible owner. Follows the same
  // rule as the disabled buttons, so Escape and the backdrop agree with them.
  const pendingRef = useRef(pending)
  useEffect(() => {
    pendingRef.current = pending
  }, [pending])

  const cancel = () => {
    if (pendingRef.current) return
    onCancelRef.current()
  }

  useEffect(() => {
    if (!open) return

    // Remember what had focus so it can be handed back on close.
    restoreRef.current = document.activeElement as HTMLElement | null
    cancelRef.current?.focus()

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        cancel()
        return
      }
      // Trap Tab to the two buttons — there is nothing else to reach. If focus
      // has drifted somewhere else (a click on the dialog body), pull it back in
      // rather than letting Tab walk out into the page behind.
      if (e.key !== 'Tab') return
      const focusables = [cancelRef.current, cancelRef.current?.nextElementSibling].filter(
        (el): el is HTMLElement => !!el
      )
      if (focusables.length === 0) return
      const first = focusables[0]
      const last = focusables[focusables.length - 1]
      const active = document.activeElement as HTMLElement | null
      if (!active || !focusables.includes(active)) {
        e.preventDefault()
        first.focus()
      } else if (e.shiftKey && active === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && active === last) {
        e.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      restoreRef.current?.focus?.()
    }
  }, [open])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center p-4"
      role="presentation"
      onClick={cancel}
    >
      <div className="absolute inset-0 bg-campus-950/40 backdrop-blur-[2px] animate-fade-in" aria-hidden="true" />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby={body ? 'confirm-dialog-body' : undefined}
        onClick={e => e.stopPropagation()}
        className="relative w-full max-w-md rounded-2xl border border-campus-900/[0.08] bg-white shadow-elevated p-5 sm:p-6 animate-scale-in"
      >
        <p id="confirm-dialog-title" className="font-display font-semibold text-[17px] text-campus-950 tracking-tight">
          {title}
        </p>
        {body && (
          <div id="confirm-dialog-body" className="text-body-sm text-surface-600 mt-2 leading-relaxed">
            {body}
          </div>
        )}
        <div className="flex justify-end gap-2 mt-5">
          <Button ref={cancelRef} size="sm" variant="ghost" onClick={cancel} disabled={pending}>
            {cancelLabel}
          </Button>
          <Button
            size="sm"
            variant={destructive ? 'destructive' : 'primary'}
            onClick={onConfirm}
            loading={pending}
            disabled={pending}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
