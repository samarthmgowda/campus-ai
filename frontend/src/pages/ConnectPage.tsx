import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader } from '../components/ui/PageState'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { UserPlus } from 'lucide-react'
import { cn } from '../utils/cn'

function ConnectSection({ title, children, active }: { title: string; children: React.ReactNode; active: boolean }) {
  return (
    <div className={cn(
      'flex flex-col gap-6 p-6 rounded-3xl border transition-all duration-200',
      active ? 'bg-white border-campus-300 shadow-card' : 'bg-surface-50 border-campus-900/[0.05] opacity-60'
    )}>
      <div className="flex items-center justify-between mb-2">
        <h3 className="font-display font-bold text-[20px] text-campus-950">{title}</h3>
        {active && <Badge variant="accent">Active</Badge>}
      </div>
      {children}
    </div>
  )
}

type ConnectTab = 'network' | 'grow' | 'mentorship'

export default function ConnectPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initial: ConnectTab =
    searchParams.get('tab') === 'mentorship' ? 'mentorship'
    : searchParams.get('tab') === 'grow' ? 'grow'
    : 'network'
  const [activeTab, setActiveTab] = useState<ConnectTab>(initial)

  // The standalone /mentorship tab now redirects here with ?tab=mentorship,
  // so honour that on load and keep the URL in step with the visible tab.
  useEffect(() => {
    const wanted = searchParams.get('tab')
    if (wanted && wanted !== activeTab && (wanted === 'mentorship' || wanted === 'grow' || wanted === 'network')) {
      setActiveTab(wanted)
    }
  }, [searchParams])

  const selectTab = (tab: ConnectTab) => {
    setActiveTab(tab)
    setSearchParams(tab === 'network' ? {} : { tab }, { replace: true })
  }

  return (
    <PageContainer width="reading">
      <PageHeader
        title="Connect"
        sub="Build your professional network and find growth opportunities"
      />

      <div className="flex items-center gap-2 mb-8 p-1 bg-surface-100 rounded-2xl w-fit">
        {(['network', 'grow', 'mentorship'] as const).map(tab => (
          <button
            key={tab}
            onClick={() => selectTab(tab)}
            className={cn(
              'px-5 py-2 rounded-xl text-body-sm font-medium transition-all',
              activeTab === tab
                ? 'bg-white text-campus-700 shadow-sm'
                : 'text-surface-500 hover:text-campus-600'
            )}
          >
            {tab.charAt(0).toUpperCase() + tab.slice(1)}
          </button>
        ))}
      </div>

      <div className="grid gap-8">
        {activeTab === 'network' && (
          <ConnectSection title="Your Network" active={true}>
            <div className="flex flex-col gap-4">
              <div className="flex items-center justify-between mb-4">
                <p className="text-body-sm text-surface-500">Manage your connections and incoming requests</p>
                <Button variant="outline" size="sm">
                  <UserPlus className="h-4 w-4 mr-2" /> Find Peers
                </Button>
              </div>
              <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {[1, 2, 3].map(i => (
                  <div key={i} className="p-4 rounded-2xl border border-campus-900/[0.05] bg-white flex items-center gap-3">
                    <div className="h-10 w-10 rounded-full bg-surface-200 flex-shrink-0" />
                    <div className="min-w-0">
                      <p className="text-body-sm font-semibold text-campus-950 truncate">Connection {i}</p>
                      <p className="text-caption text-surface-400 truncate">Student · CSE</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </ConnectSection>
        )}

        {activeTab === 'grow' && (
          <ConnectSection title="Grow" active={true}>
            <div className="flex flex-col gap-4">
              <p className="text-body-sm text-surface-500">Suggested peers based on your interests and skills</p>
              <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {[1, 2, 3].map(i => (
                  <div key={i} className="p-4 rounded-2xl border border-campus-900/[0.05] bg-white flex items-center justify-between">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="h-10 w-10 rounded-full bg-surface-200 flex-shrink-0" />
                      <div className="min-w-0">
                        <p className="text-body-sm font-semibold text-campus-950 truncate">Suggested {i}</p>
                        <p className="text-caption text-surface-400 truncate">Alumni · Google</p>
                      </div>
                    </div>
                    <Button variant="outline" size="sm" className="h-8 px-3">Connect</Button>
                  </div>
                ))}
              </div>
            </div>
          </ConnectSection>
        )}

        {activeTab === 'mentorship' && (
          <ConnectSection title="Mentorship" active={true}>
            <div className="flex flex-col gap-4">
              <p className="text-body-sm text-surface-500">Find a mentor or request guidance from experienced alumni</p>
              <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {[1, 2, 3].map(i => (
                  <div key={i} className="p-4 rounded-2xl border border-campus-900/[0.05] bg-white flex flex-col gap-3">
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 rounded-full bg-surface-200 flex-shrink-0" />
                      <div className="min-w-0">
                        <p className="text-body-sm font-semibold text-campus-950 truncate">Mentor {i}</p>
                        <p className="text-caption text-surface-400 truncate">Alumni · Senior Dev</p>
                      </div>
                    </div>
                    <Button variant="accent" size="sm" className="w-full">Request Mentorship</Button>
                  </div>
                ))}
              </div>
            </div>
          </ConnectSection>
        )}
      </div>
    </PageContainer>
  )
}
