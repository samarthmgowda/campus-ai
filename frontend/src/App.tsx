import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import Layout from './components/Layout'
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
import LoginPage from './pages/LoginPage'
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
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<Layout />}>
            <Route path="/" element={<Navigate to="/home" replace />} />
            <Route path="/home" element={<HomePage />} />
            <Route path="/chat" element={<ChatPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/reviews" element={<ReviewsPage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/dashboard/alumni" element={<AlumniDashboardPage />} />
            <Route path="/dashboard/recruiter" element={<RecruiterDashboardPage />} />
            <Route path="/skills-graph" element={<SkillsGraphPage />} />
            <Route path="/roadmaps" element={<RoadmapPage />} />
            <Route path="/referrals" element={<ReferralPage />} />
            <Route path="/documents" element={<DocumentsPage />} />
            <Route path="/projects" element={<ProjectsPage />} />
            <Route path="/events" element={<EventsPage />} />
            <Route path="/community" element={<CommunityPage />} />
            <Route path="/messages" element={<MessagesPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/mentorship" element={<MentorshipPage />} />
            <Route path="/recruiter" element={<RecruiterPage />} />
            <Route path="/admin" element={<AdminPage />} />
            <Route path="/eval" element={<EvalPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/chat" replace />} />
        </Routes>
        <ReactQueryDevtools initialIsOpen={false} />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App