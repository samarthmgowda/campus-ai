import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import Layout from './components/Layout'
import ErrorBoundary from './components/ErrorBoundary'
import { RequireAuth, RequireRole, RedirectIfSignedIn } from './components/RouteGuards'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import HomePage from './pages/HomePage'
import ChatPage from './pages/ChatPage'
import SearchPage from './pages/SearchPage'
import ReviewsPage from './pages/ReviewsPage'
import RoadmapPage from './pages/RoadmapPage'
import ReferralPage from './pages/ReferralPage'
import SkillsGraphPage from './pages/SkillsGraphPage'
import DashboardPage from './pages/DashboardPage'
import AlumniDashboardPage from './pages/AlumniDashboardPage'
import RecruiterDashboardPage from './pages/RecruiterDashboardPage'
import DocumentsPage from './pages/DocumentsPage'
import ProjectsPage from './pages/ProjectsPage'
import EventsPage from './pages/EventsPage'
import CommunityPage from './pages/CommunityPage'
import MessagesPage from './pages/MessagesPage'
import NotificationsPage from './pages/NotificationsPage'
import MentorshipPage from './pages/MentorshipPage'
import RecruiterPage from './pages/RecruiterPage'
import AdminPage from './pages/AdminPage'
import AnalyticsPage from './pages/AnalyticsPage'
import EvalPage from './pages/EvalPage'
import ProfilePage from './pages/ProfilePage'
import SettingsPage from './pages/SettingsPage'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
            {/* Public. The landing page is the front door; /login is the way in. */}
            <Route path="/" element={<LandingPage />} />
            <Route element={<RedirectIfSignedIn />}>
              <Route path="/login" element={<LoginPage />} />
            </Route>

            {/* Everything below requires a chosen profile. */}
            <Route element={<RequireAuth />}>
              <Route element={<Layout />}>
                <Route path="/home" element={<HomePage />} />
                <Route path="/chat" element={<ChatPage />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/reviews" element={<ReviewsPage />} />
                <Route path="/roadmaps" element={<RoadmapPage />} />
                <Route path="/referrals" element={<ReferralPage />} />
                <Route path="/documents" element={<DocumentsPage />} />
                <Route path="/projects" element={<ProjectsPage />} />
                <Route path="/events" element={<EventsPage />} />
                <Route path="/community" element={<CommunityPage />} />
                <Route path="/messages" element={<MessagesPage />} />
                <Route path="/notifications" element={<NotificationsPage />} />
                <Route path="/mentorship" element={<MentorshipPage />} />
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="/settings" element={<SettingsPage />} />

                {/* Role-specific destinations. The guard redirects the wrong
                    role to its own dashboard rather than showing it 403s. */}
                <Route element={<RequireRole allow={['student']} />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/skills-graph" element={<SkillsGraphPage />} />
                  <Route path="/admin" element={<AdminPage />} />
                  <Route path="/eval" element={<EvalPage />} />
                </Route>
                <Route element={<RequireRole allow={['alumni']} />}>
                  <Route path="/dashboard/alumni" element={<AlumniDashboardPage />} />
                </Route>
                <Route element={<RequireRole allow={['recruiter']} />}>
                  <Route path="/dashboard/recruiter" element={<RecruiterDashboardPage />} />
                </Route>
                <Route element={<RequireRole allow={['student', 'alumni']} />}>
                  <Route path="/recruiter" element={<RecruiterPage />} />
                </Route>

                <Route path="/analytics" element={<AnalyticsPage />} />
              </Route>
            </Route>

            {/* A real 404 rather than a silent redirect, so a bad link is
                visible instead of quietly dumping the visitor somewhere else. */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
          <ReactQueryDevtools initialIsOpen={false} />
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

export default App
