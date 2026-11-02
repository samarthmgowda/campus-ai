// Backend API response types

export interface DemoUser {
  id: number
  role: 'student' | 'alumni' | 'recruiter'
  name: string
  branch?: string
  year?: number
  skills?: string
  currentRole?: string
  currentCompany?: string
  company?: string
  roleTitle?: string
  availability?: string
}

export interface Student {
  id: number
  name: string
  branch: string
  year: number
  skills: string
  interests: string
  bio: string
  githubUrl: string
  linkedinUrl: string
  availability: string
  suspended: boolean
}

export interface Alumni {
  id: number
  name: string
  branch: string
  graduationYear: number
  skills: string
  currentRole: string
  currentCompany: string
  bio: string
  linkedinUrl: string
  availability: string
}

export interface Document {
  id: number
  title: string
  content: string
  hidden: boolean
  uploaderId: number | null
  createdAt: string
}

export interface DocumentChunk {
  id: number
  documentId: number
  content: string
  pageNumber: number
  section: string
  embeddingDimension: number
}

export interface Project {
  id: number
  name: string
  description: string
  domain: string
  requiredSkills: string
  status: string
  ownerId: number
  ownerName: string
  hidden: boolean
  createdAt: string
}

export interface ProjectMember {
  id: number
  projectId: number
  studentId: number
  studentName: string
  joinedAt: string
}

export interface Event {
  id: number
  title: string
  type: string
  eventDate: string
  description: string
  location: string
  hidden: boolean
  createdAt: string
}

export interface EventAttendance {
  id: number
  eventId: number
  studentId: number
  status: string
  joinedAt: string
}

export interface CommunityPost {
  id: number
  authorId: number
  authorName: string
  content: string
  hidden: boolean
  createdAt: string
}

export interface PostLike {
  id: number
  postId: number
  studentId: number
  createdAt: string
}

export interface PostComment {
  id: number
  postId: number
  authorId: number
  authorName: string
  content: string
  hidden: boolean
  createdAt: string
}

export interface Message {
  id: number
  senderId: number
  senderName: string
  recipientId: number
  recipientName: string
  content: string
  readFlag: boolean
  createdAt: string
}

export interface Conversation {
  peerId: number
  peerName: string
  lastMessageId: number
  preview: string
  lastAt: string
  direction: 'in' | 'out'
  unreadCount: number
}

export interface Notification {
  id: number
  recipientId: number
  type: string
  title: string
  body: string
  refType: string
  refId: number
  readFlag: boolean
  createdAt: string
}

export interface SkillEndorsement {
  id: number
  endorserId: number
  endorserName: string
  endorseeId: number
  endorseeName: string
  skill: string
  createdAt: string
}

export interface Follow {
  id: number
  followerId: number
  followerName: string
  followingId: number
  followingName: string
  createdAt: string
}

export interface Bookmark {
  id: number
  studentId: number
  refType: string
  refId: number
  createdAt: string
}

export interface RecruiterPosting {
  id: number
  recruiterId: number
  recruiterName: string
  company: string
  title: string
  description: string
  requiredSkills: string
  status: 'OPEN' | 'CLOSED'
  createdAt: string
  applicantCount: number
}

export interface PostingApplication {
  id: number
  postingId: number
  studentId: number
  studentName: string
  status: 'APPLIED' | 'SHORTLISTED' | 'REJECTED'
  createdAt: string
}

export interface MentorshipRequest {
  id: number
  studentId: number
  studentName: string
  alumniId: number
  alumniName: string
  message: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED'
  createdAt: string
}

export interface ChatTurn {
  id: number
  userId: number
  question: string
  answer: string
  answered: boolean
  sources: ChatSource[]
  createdAt: string
}

export interface ChatSource {
  documentId?: number | null
  documentTitle: string
  section?: string | null
  pageNumber?: number | null
  similarity?: number
  hybridScore?: number
  rerankScore?: number | null
}

export interface ProfileCompleteness {
  score: number
  missingFields: string[]
  level: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'
}

export interface SkillRecommendation {
  skill: string
  count: number
  reason: string
}

export interface SkillGlossaryEntry {
  canonical: string
  aliases: string[]
  related: string[]
  studentCount: number
  alumniCount: number
}

export interface ActivityItem {
  type: string
  id: number
  title: string
  preview?: string
  at: string
  refType: string
  refId: number
  status?: string
  likes?: number
  comments?: number
  readFlag?: boolean
}

export interface ApiError {
  error: string
  timestamp?: string
  status?: number
}

/** Response from POST /api/agent (AgentOrchestrator) */
export interface AgentResponse {
  type: 'success' | 'info' | 'error' | 'clarification' | 'confirmation'
  text: string
  data: Record<string, unknown> | null
  suggestions?: string[] | null
}

/** Response from POST /api/chat (ChatOrchestrator) */
export interface ChatApiResponse {
  answer: string
  userId: number | null
  question: string
}