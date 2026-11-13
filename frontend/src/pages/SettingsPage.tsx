import { PageContainer } from '../components/ui/PageContainer'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { PageHeader } from '../components/ui/PageState'
import { useSession, resetSession, ROLE_LABELS } from '../utils/identity'
import { LogOut, UserRound, Pencil, ArrowRight } from 'lucide-react'

export default function SettingsPage() {
  const navigate = useNavigate()
  const session = useSession()

  const name = session?.name ?? null
  const role = session?.role ?? null
  const accountId = session?.userId ?? null

  const handleSignOut = () => {
    // Same boundary as the sidebar: clear the caches, then the token.
    resetSession('logout')
    navigate('/login', { replace: true })
  }

  return (
    <PageContainer width="reading">
      <PageHeader title="Settings" sub="Your account preferences" />

      <section aria-label="Signed in as" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card overflow-hidden mb-4">
        <div className="flex items-center gap-4 p-5 sm:p-6">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-[#667eea] to-[#764ba2] text-white font-display font-bold text-[22px] flex-shrink-0" aria-hidden="true">
            {name ? name.charAt(0).toUpperCase() : <UserRound className="h-6 w-6 text-accent-300" />}
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-micro font-semibold text-surface-400 tracking-wide">SIGNED IN AS</p>
            <p className="font-display font-semibold text-[18px] text-campus-950 tracking-tight truncate mt-0.5">
              {name ?? 'Unknown account'}
              {role && <span className="text-surface-400"> · {ROLE_LABELS[role]}</span>}
            </p>
            <p className="text-caption text-surface-400 mt-0.5">
              Account #{accountId ?? '—'}
            </p>
          </div>
        </div>
        <div className="px-5 sm:px-6 pb-5 sm:pb-6 flex flex-wrap gap-2">
          <Link to="/profile">
            <Button variant="outline" size="sm">
              <Pencil className="h-4 w-4" /> Edit profile
            </Button>
          </Link>
          <Button variant="outline" size="sm" onClick={handleSignOut}>
            <LogOut className="h-4 w-4" /> Sign out
          </Button>
        </div>
      </section>

      <section aria-label="Profile" className="rounded-2xl border border-campus-900/[0.07] bg-white shadow-card p-5 sm:p-6">
        <p className="font-display font-semibold text-[16px] text-campus-950">Your profile</p>
        <p className="text-caption text-surface-400 mt-0.5">
          Update your bio, skills, branch, interests and social links so peers, mentors and recruiters can find you.
        </p>
        <Link
          to="/profile"
          className="mt-4 inline-flex items-center gap-2 rounded-xl border border-campus-900/[0.08] bg-campus-50/60 px-4 py-2.5 text-body-sm font-semibold text-campus-700 hover:bg-campus-50 transition-colors"
        >
          <Pencil className="h-4 w-4" aria-hidden="true" />
          Open profile editor
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </section>
    </PageContainer>
  )
}
