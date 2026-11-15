import { useNavigate } from 'react-router-dom'
import type { EntityCard } from '../../types'
import { User, Calendar, Folder, FileText, ExternalLink, Book, GraduationCap } from 'lucide-react'
import { cn } from '../../utils/cn'

interface EntityCardsProps {
  cards?: EntityCard[] | null
  title?: string
}

/**
 * Type → tint lookup for the card's icon chip.
 *
 * These are the dark-canvas tints (translucent fill + the hue as text) rather
 * than the old pastel `bg-blue-50 text-blue-600` pairs, which rendered as
 * bright white blocks on the midnight canvas.
 */
const TYPE_TINT: Record<string, string> = {
  student: 'bg-er-500/12 text-er-300',
  event: 'bg-emerald-500/12 text-emerald-300',
  project: 'bg-orange-500/12 text-orange-300',
  document: 'bg-sky-500/12 text-sky-300',
  roadmap: 'bg-rose-500/12 text-rose-300',
  alumni: 'bg-teal-500/12 text-teal-300',
}

/**
 * Renders the clickable entity cards attached to an answer — teammate
 * profiles, events, projects, documents, roadmaps, alumni. Each card
 * deep-links to the full record, so a "here are your matches" reply is
 * navigable rather than prose.
 */
export function EntityCards({ cards, title }: EntityCardsProps) {
  const navigate = useNavigate()

  if (!cards || cards.length === 0) return null

  return (
    <div className="mt-4 space-y-3">
      {title && <p className="text-micro font-semibold text-ink-500 uppercase tracking-wider">{title}</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {cards.map((card, i) => (
          <button
            key={i}
            onClick={() => navigate(card.href)}
            className="group flex items-start gap-3 text-left rounded-2xl border border-white/[0.08] bg-er-800 p-4 shadow-card hover:shadow-card-hover hover:border-er-500/35 transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-er-500"
          >
            <div className={cn(
              'flex h-10 w-10 items-center justify-center rounded-xl flex-shrink-0',
              TYPE_TINT[card.type] ?? 'bg-white/[0.06] text-ink-300'
            )}>
              {card.type === 'student' && <User className="h-5 w-5" />}
              {card.type === 'event' && <Calendar className="h-5 w-5" />}
              {card.type === 'project' && <Folder className="h-5 w-5" />}
              {card.type === 'document' && <FileText className="h-5 w-5" />}
              {card.type === 'roadmap' && <Book className="h-5 w-5" />}
              {card.type === 'alumni' && <GraduationCap className="h-5 w-5" />}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between gap-2">
                <p className="font-display font-semibold text-body-sm text-ink-100 truncate group-hover:text-er-300 transition-colors">
                  {card.title}
                </p>
                <ExternalLink className="h-3 w-3 text-ink-500 group-hover:text-er-400 transition-colors flex-shrink-0" />
              </div>
              <p className="text-caption text-ink-300 truncate">{card.subtitle}</p>
              <p className="text-micro text-ink-500 mt-1">{card.meta}</p>
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}
