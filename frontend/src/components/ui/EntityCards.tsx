import { useNavigate } from 'react-router-dom'
import type { EntityCard } from '../../types'
import { User, Calendar, Folder, FileText, ExternalLink, Book, GraduationCap } from 'lucide-react'
import { cn } from '../../utils/cn'

interface EntityCardsProps {
  cards?: EntityCard[] | null
  title?: string
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
      {title && <p className="text-micro font-semibold text-surface-400 uppercase tracking-wider">{title}</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {cards.map((card, i) => (
          <button
            key={i}
            onClick={() => navigate(card.href)}
            className="group text-left rounded-2xl border border-campus-900/[0.08] bg-white p-4 shadow-card hover:shadow-card-hover hover:border-campus-900/20 transition-all duration-200"
          >
            <div className={cn(
              "flex h-10 w-10 items-center justify-center rounded-xl flex-shrink-0",
              card.type === 'student' && "bg-blue-50 text-blue-600",
              card.type === 'event' && "bg-emerald-50 text-emerald-600",
              card.type === 'project' && "bg-amber-50 text-amber-600",
              card.type === 'document' && "bg-purple-50 text-purple-600",
              card.type === 'roadmap' && "bg-pink-50 text-pink-600",
              card.type === 'alumni' && "bg-teal-50 text-teal-600",
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
                <p className="font-display font-semibold text-body-sm text-campus-950 truncate group-hover:text-campus-700 transition-colors">
                  {card.title}
                </p>
                <ExternalLink className="h-3 w-3 text-surface-300 group-hover:text-campus-500 transition-colors flex-shrink-0" />
              </div>
              <p className="text-caption text-surface-500 truncate">{card.subtitle}</p>
              <p className="text-micro text-surface-400 mt-1">{card.meta}</p>
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}