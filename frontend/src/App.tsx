import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import Layout from './components/Layout'
import ErrorBoundary from './components/ErrorBoundary'
import ConnectionGuard from './components/ConnectionGuard'
import SessionBootstrap from './components/SessionBootstrap'
import { ChatProvider } from './hooks/useChat'
import { onSessionReset } from './utils/identity'
import { RequireAuth, RequireRole, RedirectIfSignedIn } from './components/RouteGuards'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import HomePage from './pages/HomePage'
import ChatPage from './pages/ChatPage'
import SearchPage from './pages/SearchPage'
import SkillsGraphPage from './pages/SkillsGraphPage'
import DashboardPage from './pages/DashboardPage'
import AlumniDashboardPage from './pages/AlumniDashboardPage'
import RecruiterDashboardPage from './pages/RecruiterDashboardPage'
import DocumentsPage from './pages/DocumentsPage'
import ProjectsPage from './pages/ProjectsPage'
import EventsPage from './pages/EventsPage'
import EventDetailPage from './pages/EventDetailPage'
import PublicProfilePage from './pages/PublicProfilePage'
import CommunityPage from './pages/CommunityPage'
import MessagesPage from './pages/MessagesPage'
import NotificationsPage from './pages/NotificationsPage'
import RecruiterPage from './pages/RecruiterPage'
import AdminPage from './pages/AdminPage'
import AnalyticsPage from './pages/AnalyticsPage'
import EvalPage from './pages/EvalPage'
import ProfilePage from './pages/ProfilePage'
import SettingsPage from './pages/SettingsPage'
import ConnectPage from './pages/ConnectPage'
import TeamRequestsPage from './pages/TeamRequestsPage'
import PlacementTrainingPage from './pages/PlacementTrainingPage'
import './index.css'

// The home dashboard is a read-only fan-out over data other pages mutate
// (events, projects, notifications, the feed). It cannot know which page
// changed what, so any successful mutation anywhere drops it here and it
// refetches on next visit rather than showing a stale count.
const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['home'] })
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // A 401 is already handled by the client (session torn down, redirect to
      // /login); retrying it just fires the same doomed request again. A 403
      // is a decision, not a blip — retrying cannot change the answer.
      retry: (failureCount, error) => {
        const status = (error as { status?: number } | null)?.status
        if (status === 401 || status === 403 || status === 404) return false
        return failureCount < 1
      },
    },
    mutations: {
      retry: false,
    },
  },
})

/**
 * One cache for the whole app, wiped at every session boundary.
 *
 * This cache is module-level and outlives the components that read it, so a
 * logout that only removed the token left every page's data in memory. The
 * next account to sign in on the same tab was served the previous account's
 * responses for up to `staleTime` — the most visible form of the cross-user
 * bleed. Clearing here means "signed out" and "signed in as somebody else"
 * are both genuinely empty states, and any query key that forgets to include
 * the account id cannot leak, because there is nothing left to leak.
 */
onSessionReset(() => {
  queryClient.clear()
})

function App() {
  return (
    <ErrorBoundary>
      <ConnectionGuard>
        <SessionBootstrap>
          <QueryClientProvider client={queryClient}>
            <ChatProvider>
              <BrowserRouter>
          <Routes>
            {/* Public. The landing page is the front door; /login is the way in. */}
            <Route path="/" element={<LandingPage />} />
            <Route element={<RedirectIfSignedIn />}>
              <Route path="/login" element={<LoginPage />} />
            </Route>

            {/* Full-page chat, reached by expanding the floating widget. It is
                its own shell (own header, no sidebar), so it sits outside Layout. */}
            <Route element={<RequireAuth />}>
              <Route path="/chat/full" element={<ChatPage fullPage />} />
            </Route>

            {/* Everything below requires a chosen profile. */}
            <Route element={<RequireAuth />}>
              <Route element={<Layout />}>
                <Route path="/home" element={<HomePage />} />
                <Route path="/chat" element={<ChatPage />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/projects" element={<ProjectsPage />} />
                <Route path="/events" element={<EventsPage />} />
                <Route path="/events/:id" element={<EventDetailPage />} />
                <Route path="/profiles/:id" element={<PublicProfilePage />} />
                <Route path="/community" element={<CommunityPage />} />
                <Route path="/messages" element={<MessagesPage />} />
                <Route path="/notifications" element={<NotificationsPage />} />
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="/settings" element={<SettingsPage />} />

                {/* Redundant standalone routes now live inside their hubs.
                    Keep the old paths working by redirecting rather than 404ing. */}
                <Route path="/mentorship" element={<Navigate to="/connect?tab=mentorship" replace />} />
                <Route path="/roadmaps" element={<Navigate to="/placement-training?tab=roadmaps" replace />} />
                <Route path="/reviews" element={<Navigate to="/community" replace />} />
                <Route path="/referrals" element={<Navigate to="/placement-training" replace />} />

                {/* Role-specific destinations. The guard redirects the wrong
                    role to its own dashboard rather than showing it 403s. */}
                <Route element={<RequireRole allow={['student', 'alumni', 'recruiter']} />}>
                  <Route path="/connect" element={<ConnectPage />} />
                  <Route path="/team-requests" element={<TeamRequestsPage />} />
                  <Route path="/placement-training" element={<PlacementTrainingPage />} />
                </Route>
                <Route element={<RequireRole allow={['student']} />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/skills-graph" element={<SkillsGraphPage />} />
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
                {/* Admin-only. The backend enforces this too (role-gated matchers);
                    the guard just keeps a non-admin off a screen of 403s.
                    Library, Analytics and Evaluation are all admin surfaces now. */}
                <Route element={<RequireRole allow={['admin']} />}>
                  <Route path="/documents" element={<DocumentsPage />} />
                  <Route path="/analytics" element={<AnalyticsPage />} />
                  <Route path="/admin" element={<AdminPage />} />
                  <Route path="/eval" element={<EvalPage />} />
                </Route>
              </Route>
            </Route>

            {/* A real 404 rather than a silent redirect, so a bad link is
                visible instead of quietly dumping the visitor somewhere else. */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
          {/* Dev-only: this must never reach the production bundle, so it is
              guarded by the compile-time flag rather than a runtime check. */}
          {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
        </BrowserRouter>
        </ChatProvider>
      </QueryClientProvider>
      </SessionBootstrap>
      </ConnectionGuard>
    </ErrorBoundary>
  )
}

export default App
