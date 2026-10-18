package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.ProjectMember;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.campusconnect.campus_ai.repository.ModerationReportRepository;
import com.campusconnect.campus_ai.repository.SkillEndorsementRepository;
import com.campusconnect.campus_ai.service.NotificationService;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectToolService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;
    private final SkillGraphService skillGraphService;
    private final SkillEndorsementRepository endorsementRepository;
    private final ModerationReportRepository moderationRepository;
    private final NotificationService notificationService;

    public ProjectToolService(ProjectRepository projectRepository,
                              ProjectMemberRepository memberRepository,
                              StudentRepository studentRepository,
                              CurrentUserService currentUserService,
                              SkillGraphService skillGraphService,
                              SkillEndorsementRepository endorsementRepository,
                              ModerationReportRepository moderationRepository,
                              NotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
        this.skillGraphService = skillGraphService;
        this.endorsementRepository = endorsementRepository;
        this.moderationRepository = moderationRepository;
        this.notificationService = notificationService;
    }

    // ——— records (DTOs, never entities) ———

    public record ProjectSummary(Long id, String name, String domain, String requiredSkills, String status, String ownerName) {}
    public record MemberInfo(Long studentId, String name, String branch, Integer year, String availability, String role, List<String> skills) {}
    public record ProjectDetails(Long id, String name, String description, String domain, List<String> requiredSkills, List<MemberInfo> members) {}
    public record CandidateMatch(Long studentId, String name, String branch, Integer year, String skills, String availability, List<String> matchedSkills) {}
    public record TeammateSuggestions(String projectName, List<String> gaps, List<CandidateMatch> candidates) {}

    // ——— 1. getMyProjects ———

    public List<ProjectSummary> getMyProjects(Long userId) {
        currentUserService.requireUser(userId);
        List<Project> all = projectRepository.findAll();
        List<ProjectSummary> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Project p : all) {
            if (!"ACTIVE".equalsIgnoreCase(p.getStatus())) continue;
            if ("HIDDEN".equalsIgnoreCase(p.getStatus())) continue;
            if (seen.contains(p.getId())) continue;
            if (!isOwnerOrMember(p, userId)) continue;
            seen.add(p.getId());
            result.add(toSummary(p));
        }
        result.sort(Comparator.comparing(ProjectSummary::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    // ——— 2. getProjectDetails ———

    public ProjectDetails getProjectDetails(Long userId, Long projectId) {
        currentUserService.requireUser(userId);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
        if ("HIDDEN".equalsIgnoreCase(p.getStatus())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project hidden by moderation");
        if (!isMember(p, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of project: " + p.getName());
        }
        List<String> required = splitComma(p.getRequiredSkills());
        List<ProjectMember> members = memberRepository.findByProjectId(p.getId());
        List<MemberInfo> memberInfos = members.stream().map(m -> {
            Student s = m.getStudent();
            return new MemberInfo(s.getId(), s.getName(), s.getBranch(), s.getYear(), s.getAvailability(), m.getRole(), splitComma(s.getSkills()));
        }).sorted(Comparator.comparing(MemberInfo::name, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
        return new ProjectDetails(p.getId(), p.getName(), p.getDescription(), p.getDomain(), required, memberInfos);
    }

    // ——— 3. getSkillGaps ———

    public List<String> getSkillGaps(Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
        List<String> required = splitComma(p.getRequiredSkills());
        if (required.isEmpty()) return List.of();
        Set<String> memberCanon = new HashSet<>();
        for (ProjectMember m : memberRepository.findByProjectId(p.getId())) {
            for (String s : splitComma(m.getStudent().getSkills())) {
                String c = skillGraphService.normalize(s);
                if (c == null) c = s.trim();
                memberCanon.add(c.toLowerCase(Locale.ROOT));
            }
        }
        List<String> gaps = new ArrayList<>();
        for (String req : required) {
            String rc = skillGraphService.normalize(req);
            if (rc == null) rc = req.trim();
            if (!memberCanon.contains(rc.toLowerCase(Locale.ROOT))) {
                gaps.add(req);
            }
        }
        return gaps;
    }

    // ——— 4. searchStudents ———

    public List<CandidateMatch> searchStudents(List<String> skills, boolean availableOnly, Collection<Long> excludeStudentIds) {
        if (skills == null || skills.isEmpty()) return List.of();
        List<String> requestedRaw = skills.stream().map(s -> s == null ? "" : s.trim()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (requestedRaw.isEmpty()) return List.of();
        List<String> requested = requestedRaw.stream().map(r -> { String n = skillGraphService.normalize(r); return n != null ? n : r; }).collect(Collectors.toList());
        Set<Long> exclude = excludeStudentIds == null ? Set.of() : new HashSet<>(excludeStudentIds);
        Map<String, String> lowerToOriginal = new LinkedHashMap<>();
        for (String r : requested) lowerToOriginal.putIfAbsent(r.toLowerCase(Locale.ROOT), r);

        List<CandidateMatch> matches = new ArrayList<>();
        for (Student s : studentRepository.findAll()) {
            if (exclude.contains(s.getId())) continue;
            if (availableOnly && "BUSY".equalsIgnoreCase(s.getAvailability())) continue;
            Set<String> studentLower = splitComma(s.getSkills()).stream().map(x -> { String n = skillGraphService.normalize(x); if (n == null) n = x; return n.toLowerCase(Locale.ROOT); }).collect(Collectors.toSet());
            List<String> matched = new ArrayList<>();
            for (Map.Entry<String, String> e : lowerToOriginal.entrySet()) {
                if (studentLower.contains(e.getKey())) matched.add(e.getValue());
            }
            if (matched.isEmpty()) continue;
            matches.add(new CandidateMatch(s.getId(), s.getName(), s.getBranch(), s.getYear(), s.getSkills(), s.getAvailability(), matched));
        }
        // Trust-weighted: more endorsements on matched skills nudges up, reported students sink
        matches.sort(Comparator
                .comparingInt((CandidateMatch c) -> c.matchedSkills().size()).reversed()
                .thenComparingLong((CandidateMatch c) -> c.matchedSkills().stream().mapToLong(sk -> endorsementRepository.countByEndorseeIdAndSkillIgnoreCase(c.studentId(), sk)).sum()).reversed()
                .thenComparing(c -> hasOpenReport(c.studentId()) ? 1 : 0)
                .thenComparing(c -> "AVAILABLE".equalsIgnoreCase(c.availability()) ? 0 : 1)
                .thenComparing(c -> c.name().toLowerCase(Locale.ROOT)));
        if (matches.size() > 10) return matches.subList(0, 10);
        return matches;
    }

    // ——— 5. findTeammates ———

    public TeammateSuggestions findTeammates(Long userId, Long projectId) {
        currentUserService.requireUser(userId);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
        if ("HIDDEN".equalsIgnoreCase(p.getStatus())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project hidden by moderation");
        if (!isMember(p, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of project: " + p.getName());
        }
        List<String> gaps = getSkillGaps(projectId);
        if (gaps.isEmpty()) {
            return new TeammateSuggestions(p.getName(), gaps, List.of());
        }
        Set<Long> memberIds = memberRepository.findByProjectId(p.getId()).stream()
                .map(m -> m.getStudent().getId()).collect(Collectors.toSet());
        List<CandidateMatch> candidates = searchStudents(gaps, true, memberIds);
        return new TeammateSuggestions(p.getName(), gaps, candidates);
    }

    // ——— 6. createProject ———

    public ProjectSummary createProject(Long userId, String name, String description, String domain, String requiredSkills) {
        Student owner = currentUserService.requireUser(userId);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Project name required");
        String n = name.trim();
        if (n.length() < 3) throw new IllegalArgumentException("Project name must be at least 3 characters");
        if (n.length() > 120) throw new IllegalArgumentException("Project name too long (max 120)");
        // duplicate name guard (case-insensitive)
        var existing = projectRepository.findByName(n);
        if (existing.isPresent()) {
            // also check case-insensitive via findAll fallback
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Project name already exists: " + n);
        }
        // also scan case-insensitive duplicates
        for (Project pr : projectRepository.findAll()) {
            if (pr.getName() != null && pr.getName().equalsIgnoreCase(n)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Project name already exists: " + n);
            }
        }
        String desc = description != null ? description.trim() : null;
        if (desc != null && desc.length() > 2000) throw new IllegalArgumentException("Description too long (max 2000)");
        String dom = domain != null ? domain.trim() : null;
        String reqSkills = null;
        if (requiredSkills != null && !requiredSkills.isBlank()) {
            var normalized = skillGraphService.normalizeCsv(requiredSkills);
            if (!normalized.isEmpty()) reqSkills = String.join(", ", normalized);
            else reqSkills = requiredSkills.trim();
            if (reqSkills.length() > 500) throw new IllegalArgumentException("requiredSkills too long");
        }
        Project prj = new Project(n, desc, dom, reqSkills, "ACTIVE", owner);
        prj = projectRepository.save(prj);
        // owner as member with role OWNER
        ProjectMember ownerMember = new ProjectMember(prj, owner, "OWNER");
        memberRepository.save(ownerMember);
        return toSummary(prj);
    }

    // ——— 7. addMember ———

    public MemberInfo addMember(Long requesterId, Long projectId, Long studentId) {
        currentUserService.requireUser(requesterId);
        Project prj = projectRepository.findById(projectId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Project not found: " + projectId));
        if (!"ACTIVE".equalsIgnoreCase(prj.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Project is not active");
        }
        // only owner or existing member can add
        if (!isOwnerOrMember(prj, requesterId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Only project members can add teammates");
        }
        if (studentId == null) throw new IllegalArgumentException("studentId required");
        Student toAdd = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        var members = memberRepository.findByProjectId(prj.getId());
        if (members.stream().anyMatch(m -> m.getStudent().getId().equals(studentId))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Already a member");
        }
        if (members.size() >= 10) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Project team is full (max 10)");
        }
        ProjectMember pm = new ProjectMember(prj, toAdd, "MEMBER");
        memberRepository.save(pm);
        try {
            notificationService.notifyOther(toAdd.getId(), requesterId, "PROJECT_MEMBER",
                    "Added to project: " + prj.getName(),
                    currentUserService.requireUser(requesterId).getName() + " added you to " + prj.getName(),
                    "PROJECT", prj.getId());
        } catch (Exception ignored) {}
        return new MemberInfo(toAdd.getId(), toAdd.getName(), toAdd.getBranch(), toAdd.getYear(), toAdd.getAvailability(), "MEMBER", splitComma(toAdd.getSkills()));
    }

    // ——— helpers ———

    private boolean hasOpenReport(Long studentId) {
        try {
            return !moderationRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("STUDENT", studentId)
                    .stream().filter(r -> "OPEN".equals(r.getStatus())).toList().isEmpty();
        } catch (Exception e) { return false; }
    }

    private boolean isMember(Project p, Long userId) {
        for (ProjectMember m : memberRepository.findByProjectId(p.getId())) {
            if (m.getStudent().getId().equals(userId)) return true;
        }
        return false;
    }

    private boolean isOwnerOrMember(Project p, Long userId) {
        if (p.getOwner() != null && p.getOwner().getId().equals(userId)) return true;
        return isMember(p, userId);
    }

    private ProjectSummary toSummary(Project p) {
        return new ProjectSummary(p.getId(), p.getName(), p.getDomain(), p.getRequiredSkills(), p.getStatus(), p.getOwner() != null ? p.getOwner().getName() : null);
    }

    private static List<String> splitComma(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
