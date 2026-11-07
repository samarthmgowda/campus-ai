import { useState } from 'react'
import { PageContainer } from '../components/ui/PageContainer'
import { PageHeader } from '../components/ui/PageState'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Search, Plus } from 'lucide-react'

function RequestCard({ request, onApply }: { request: any; onApply: () => void }) {
  return (
    <div className="p-5 rounded-2xl border border-campus-900/[0.06] bg-white shadow-card hover:border-campus-300 transition-all duration-200">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-full bg-surface-200" />
          <div>
            <p className="text-body-sm font-bold text-campus-950">{request.userName}</p>
            <p className="text-caption text-surface-400">{request.branch} · {request.year} Year</p>
          </div>
        </div>
        <Badge variant="outline" className="text-micro">{request.event}</Badge>
      </div>
      <div className="mb-4">
        <p className="text-body-sm font-semibold text-campus-950 mb-1">Looking for:</p>
        <p className="text-body-sm text-surface-600 leading-relaxed">
          {request.requirement}
        </p>
      </div>
      <div className="flex flex-wrap gap-2 mb-5">
        {request.skills.map((s: string) => (
          <span key={s} className="px-2 py-0.5 rounded-md bg-campus-50 border border-campus-100 text-micro font-medium text-campus-700">
            {s}
          </span>
        ))}
      </div>
      <Button variant="accent" size="sm" className="w-full" onClick={onApply}>
        Apply to Team
      </Button>
    </div>
  )
}

export default function TeamRequestsPage() {
  const [search, setSearch] = useState('')
  const [isPosting, setIsPosting] = useState(false)

  const MOCK_REQUESTS = [
    {
      id: 1,
      userName: 'Aryan Sharma',
      branch: 'CSE',
      year: 3,
      event: 'AutoHack 2026',
      requirement: 'Looking for a Frontend developer experienced in React and Tailwind. We have the backend ready, just need a sharp UI lead to polish the dashboard.',
      skills: ['React', 'Tailwind', 'Figma'],
    },
    {
      id: 2,
      userName: 'Sanya Iyer',
      branch: 'ECE',
      year: 2,
      event: 'Smart Campus Challenge',
      requirement: 'Need a teammate who knows IoT and sensors. We are building a smart parking system and need someone to handle the hardware integration.',
      skills: ['Arduino', 'ESP32', 'C++'],
    },
    {
      id: 3,
      userName: 'Rohan Das',
      branch: 'ME',
      year: 4,
      event: 'InnovateMIT',
      requirement: 'Looking for a Business/Pitch lead. We have the technical prototype for an EV charger, now we need someone to build the deck and market research.',
      skills: ['Market Research', 'Pitching', 'PPT'],
    },
  ]

  return (
    <PageContainer width="reading">
      <PageHeader
        title="Team Requests"
        sub="Find a team or post your requirements for upcoming events"
      />

      <div className="flex items-center justify-between mb-8">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-surface-400" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search by skill or event..."
            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-campus-900/[0.08] bg-white text-body-sm focus:outline-none focus:ring-2 focus:ring-campus-500"
          />
        </div>
        <Button variant="accent" size="md" className="ml-4" onClick={() => setIsPosting(true)}>
          <Plus className="h-4 w-4 mr-2" /> Post Request
        </Button>
      </div>

      {isPosting && (
        <div className="mb-8 p-6 rounded-3xl border-2 border-dashed border-campus-300 bg-campus-50/50 animate-slide-down">
          <div className="flex items-center justify-between mb-6">
            <h3 className="font-display font-bold text-[20px] text-campus-950">Create Team Request</h3>
            <Button variant="ghost" size="sm" onClick={() => setIsPosting(false)}>Cancel</Button>
          </div>
          <div className="grid sm:grid-cols-2 gap-6">
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Event Name</label>
                <input type="text" className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm" placeholder="e.g. AutoHack 2026" />
              </div>
              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Requirements</label>
                <textarea rows={4} className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm resize-none" placeholder="Describe who you are looking for..." />
              </div>
            </div>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-micro font-semibold text-surface-500 uppercase tracking-wide">Required Skills (comma separated)</label>
                <input type="text" className="w-full rounded-xl border border-campus-900/[0.08] bg-white px-4 py-2.5 text-body-sm" placeholder="React, Figma, Python" />
              </div>
              <div className="flex justify-end mt-12">
                <Button variant="accent" size="lg" onClick={() => setIsPosting(false)}>
                  Post to Campus
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {MOCK_REQUESTS.filter(r =>
          r.event.toLowerCase().includes(search.toLowerCase()) ||
          r.requirement.toLowerCase().includes(search.toLowerCase()) ||
          r.skills.some(s => s.toLowerCase().includes(search.toLowerCase()))
        ).map(req => (
          <RequestCard key={req.id} request={req} onApply={() => {}} />
        ))}
      </div>
    </PageContainer>
  )
}
