import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader } from '../components/ui/PageState'
import { Button } from '../components/ui/Button'
import { Briefcase, BookOpen, Search } from 'lucide-react'
import { cn } from '../utils/cn'

function RoadmapCard({ roadmap }: { roadmap: any }) {
  return (
    <div className="p-6 rounded-3xl border border-campus-900/[0.06] bg-white shadow-card hover:shadow-card-hover transition-all duration-200 group">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-campus-50 text-campus-600 group-hover:bg-campus-600 group-hover:text-white transition-colors">
            <Briefcase className="h-5 w-5" />
          </div>
          <div>
            <h3 className="font-display font-bold text-[18px] text-campus-950">{roadmap.role}</h3>
            <p className="text-caption text-surface-400">{roadmap.company}</p>
          </div>
        </div>
        <div className="text-right">
          <p className="text-body-sm font-bold text-campus-700">{roadmap.ctc}</p>
          <p className="text-micro text-surface-400">Expected CTC</p>
        </div>
      </div>
      <div className="grid sm:grid-cols-2 gap-6 mb-6">
        <div className="space-y-3">
          <p className="text-micro font-semibold text-surface-400 uppercase tracking-wide">Requirements</p>
          <p className="text-body-sm text-surface-600 leading-relaxed">{roadmap.requirements}</p>
        </div>
        <div className="space-y-3">
          <p className="text-micro font-semibold text-surface-400 uppercase tracking-wide">Required Skills</p>
          <div className="flex flex-wrap gap-1.5">
            {roadmap.skills.map((s: string) => (
              <span key={s} className="px-2 py-0.5 rounded-md bg-surface-100 text-micro font-medium text-surface-600 border border-surface-200">
                {s}
              </span>
            ))}
          </div>
        </div>
      </div>
      <div className="pt-4 border-t border-campus-900/[0.06] flex items-center justify-between">
        <div className="flex items-center gap-2 text-body-sm font-medium text-campus-600">
          <BookOpen className="h-4 w-4" />
          {roadmap.resources.length} Resources
        </div>
        <Button variant="outline" size="sm">View Path</Button>
      </div>
    </div>
  )
}

export default function PlacementTrainingPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialTab = searchParams.get('tab') === 'roadmaps' ? 'roadmaps' : 'careers'
  const [activeTab, setActiveTab] = useState<'careers' | 'roadmaps'>(initialTab)

  // Keep the URL in step with the visible tab, so the old /roadmaps deep link
  // (which now redirects here) and a shared link both land on the right subtab.
  useEffect(() => {
    const wanted = searchParams.get('tab')
    if (wanted && wanted !== activeTab) {
      setActiveTab(wanted === 'roadmaps' ? 'roadmaps' : 'careers')
    }
  }, [searchParams])

  const selectTab = (tab: 'careers' | 'roadmaps') => {
    setActiveTab(tab)
    setSearchParams(tab === 'careers' ? {} : { tab }, { replace: true })
  }

  const MOCK_ROADMAPS = [
    {
      id: 1,
      role: 'Backend Engineer',
      company: 'Google',
      ctc: '₹32 LPA',
      requirements: 'Strong DSA, System Design, Distributed Systems, Java/Go',
      skills: ['Java', 'Spring Boot', 'Kubernetes', 'Redis'],
      resources: ['LeetCode 75', 'Designing Data-Intensive Apps', 'System Design Primer'],
    },
    {
      id: 2,
      role: 'Product Designer',
      company: 'Apple',
      ctc: '₹24 LPA',
      requirements: 'UI/UX Portfolio, Prototyping, Human-Centered Design, Figma',
      skills: ['Figma', 'Prototyping', 'User Research', 'Design Systems'],
      resources: ['Refactoring UI', 'Don\'t Make Me Think', 'Baymard Institute'],
    },
    {
      id: 3,
      role: 'Data Scientist',
      company: 'Microsoft',
      ctc: '₹28 LPA',
      requirements: 'Advanced Stats, ML Frameworks, SQL, Python, PyTorch',
      skills: ['Python', 'PyTorch', 'SQL', 'Pandas'],
      resources: ['Andrew Ng ML Course', 'Kaggle Competitions', 'Fast.ai'],
    },
  ]

  return (
    <PageContainer width="reading">
      <PageHeader
        title="Placement & Training"
        sub="Accelerate your career with guided roadmaps and job opportunities"
      />

      <div className="flex items-center gap-2 mb-8 p-1 bg-surface-100 rounded-2xl w-fit">
        {(['careers', 'roadmaps'] as const).map(tab => (
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
            {tab === 'careers' ? 'Job Postings' : 'Career Roadmaps'}
          </button>
        ))}
      </div>

      {activeTab === 'careers' ? (
        <div className="grid gap-6">
          {/* We are reusing the logic from the old RecruiterPage but cleaning the UI */}
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-display font-bold text-[20px] text-campus-950">Open Roles</h3>
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-surface-400" />
              <input
                type="text"
                placeholder="Search roles..."
                className="text-body-sm bg-transparent border-b border-campus-900/[0.08] focus:outline-none focus:border-campus-500"
              />
            </div>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {[1, 2, 3, 4, 5, 6].map(i => (
              <div key={i} className="p-5 rounded-2xl border border-campus-900/[0.06] bg-white shadow-card hover:border-campus-300 transition-all cursor-pointer">
                <div className="flex items-center gap-3 mb-3">
                  <div className="h-10 w-10 rounded-xl bg-surface-100 flex items-center justify-center">
                    <Briefcase className="h-5 w-5 text-surface-400" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-body-sm font-bold text-campus-950 truncate">Software Engineer Intern</p>
                    <p className="text-caption text-surface-400 truncate">Amazon</p>
                  </div>
                </div>
                <div className="flex items-center gap-2 mb-4">
                  <span className="px-2 py-0.5 rounded-full bg-green-50 text-green-700 text-micro font-semibold border border-green-100">Open</span>
                  <span className="text-micro text-surface-400">Posted 2d ago</span>
                </div>
                <Button variant="outline" size="sm" className="w-full">View Details</Button>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {MOCK_ROADMAPS.map(roadmap => (
            <RoadmapCard key={roadmap.id} roadmap={roadmap} />
          ))}
        </div>
      )}
    </PageContainer>
  )
}
