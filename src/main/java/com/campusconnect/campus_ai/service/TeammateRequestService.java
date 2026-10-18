package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.ProjectMember;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.entity.TeammateRequest;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.repository.TeammateRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Teammate-request lifecycle: gap analysis → send request → accept/reject/withdraw.
 * On ACCEPTED the candidate is auto-added as a ProjectMember + both sides notified.
 * Follows the same pattern as ProjectInviteService (Task #5) but driven by skill gaps.
 */
@Service
public class TeammateRequestService {

    private final TeammateRequestRepository requestRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;
    private final ProjectToolService projectToolService;
    private final NotificationService notificationService;

    public TeammateRequestService(TeammateRequestRepository requestRepository,
                                  ProjectRepository projectRepository,
                                  ProjectMemberRepository memberRepository,
                                  StudentRepository studentRepository,
                                  CurrentUserService currentUserService,
                                  ProjectToolService projectToolService,
                                  NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
        this.projectToolService = projectToolService;
        this.notificationService = notificationService;
    }

    // ——— DTOs ———

    public record RequestDto(Long id, Long projectId, String projectName,
                             Long requesterId, String requesterName,
                             Long candidateId, String candidateName,
                             String status, String message, String matchedSkills,
                             String createdAt, String respondedAt) {}

    // ——— 1. Send request ———

    public RequestDto send(Long requesterId, Long projectId, Long candidateId, String message) {
        if (requesterId == null || projectId == null || candidateId == null)
            throw new IllegalArgumentException("requesterId, projectId and candidateId required");
        if (requesterId.equals(candidateId))
            throw new IllegalArgumentException("Cannot send a teammate request to yourself");

        Student requester = currentUserService.requireUser(requesterId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));

        if ("HIDDEN".equalsIgnoreCase(project.getStatus()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project hidden by moderation");
        if (!"ACTIVE".equalsIgnoreCase(project.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project is not active");

        // Only owner or existing member can send
        if (!isOwnerOrMember(project, requesterId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only project members can send teammate requests");

        Student candidate = studentRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        // Already a member?
        if (isAlreadyMember(project, candidateId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    candidate.getName() + " is already a member of " + project.getName());

        // Team full?
        if (memberRepository.findByProjectId(project.getId()).size() >= 10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project team is full (max 10)");

        // Existing pending request?
        var existing = requestRepository.findByProjectIdAndCandidateId(projectId, candidateId);
        if (existing.isPresent() && "PENDING".equals(existing.get().getStatus())) {
            return toDto(existing.get());
        }

        // Compute matched skills from gap analysis
        List<String> gaps = projectToolService.getSkillGaps(projectId);
        List<String> candidateSkills = splitComma(candidate.getSkills());
        List<String> matched = new ArrayList<>();
        for (String gap : gaps) {
            String gapLower = gap.toLowerCase(Locale.ROOT);
            for (String cs : candidateSkills) {
                if (cs.toLowerCase(Locale.ROOT).equals(gapLower)) {
                    matched.add(gap);
                    break;
                }
            }
        }
        String matchedCsv = matched.isEmpty() ? null : String.join(", ", matched);

        // If previously rejected/withdrawn, reset to pending
        if (existing.isPresent()) {
            TeammateRequest req = existing.get();
            req.setStatus("PENDING");
            req.setRequester(requester);
            req.setMessage(message);
            req.setMatchedSkills(matchedCsv);
            req.setCreatedAt(LocalDateTime.now());
            req.setRespondedAt(null);
            req = requestRepository.save(req);
            notifyCandidate(req, requester, project, candidate);
            return toDto(req);
        }

        TeammateRequest req = new TeammateRequest(project, requester, candidate, message, matchedCsv);
        req = requestRepository.save(req);
        notifyCandidate(req, requester, project, candidate);
        return toDto(req);
    }

    // ——— 2. Respond (accept/reject) ———

    public RequestDto respond(Long requestId, Long candidateId, String action) {
        if (requestId == null || candidateId == null || action == null)
            throw new IllegalArgumentException("requestId, candidateId and action required");

        currentUserService.requireUser(candidateId);
        TeammateRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found: " + requestId));

        if (!req.getCandidate().getId().equals(candidateId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your request");
        if (!"PENDING".equals(req.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request already " + req.getStatus().toLowerCase());

        String act = action.trim().toUpperCase();
        if (!act.equals("ACCEPT") && !act.equals("REJECT") && !act.equals("ACCEPTED") && !act.equals("REJECTED"))
            throw new IllegalArgumentException("Action must be ACCEPT or REJECT");

        boolean accept = act.startsWith("ACCEPT");
        req.setStatus(accept ? "ACCEPTED" : "REJECTED");
        req.setRespondedAt(LocalDateTime.now());
        req = requestRepository.save(req);

        if (accept) {
            // Auto-add candidate as project member
            if (!isAlreadyMember(req.getProject(), candidateId)) {
                memberRepository.save(new ProjectMember(req.getProject(), req.getCandidate(), "MEMBER"));
            }
            // Notify requester
            try {
                notificationService.notifyOther(req.getRequester().getId(), candidateId,
                        "TEAMMATE_REQUEST",
                        req.getCandidate().getName() + " accepted your teammate request",
                        "Project: " + req.getProject().getName() + " — " + req.getCandidate().getName() + " has joined the team!",
                        "TEAMMATE_REQUEST", req.getId());
            } catch (Exception ignored) {}
        } else {
            // Notify requester of rejection
            try {
                notificationService.notifyOther(req.getRequester().getId(), candidateId,
                        "TEAMMATE_REQUEST",
                        req.getCandidate().getName() + " declined your teammate request",
                        "Project: " + req.getProject().getName(),
                        "TEAMMATE_REQUEST", req.getId());
            } catch (Exception ignored) {}
        }

        return toDto(req);
    }

    // ——— 3. Withdraw (requester cancels) ———

    public RequestDto withdraw(Long requestId, Long requesterId) {
        currentUserService.requireUser(requesterId);
        TeammateRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found: " + requestId));

        // Only requester or project owner can withdraw
        if (!req.getRequester().getId().equals(requesterId)
                && !req.getProject().getOwner().getId().equals(requesterId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester or project owner can withdraw");

        if (!"PENDING".equals(req.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request already " + req.getStatus().toLowerCase());

        req.setStatus("WITHDRAWN");
        req.setRespondedAt(LocalDateTime.now());
        req = requestRepository.save(req);
        return toDto(req);
    }

    // ——— 4. List: my incoming requests ———

    public List<RequestDto> getMyRequests(Long candidateId, String status) {
        currentUserService.requireUser(candidateId);
        List<TeammateRequest> list;
        if (status != null && !status.isBlank()) {
            list = requestRepository.findByCandidateIdAndStatusOrderByCreatedAtDesc(candidateId, status.trim().toUpperCase());
        } else {
            list = requestRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ——— 5. List: requests sent by me ———

    public List<RequestDto> getSentRequests(Long requesterId) {
        currentUserService.requireUser(requesterId);
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ——— 6. List: requests for a project ———

    public List<RequestDto> getProjectRequests(Long projectId, Long userId, String status) {
        currentUserService.requireUser(userId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
        if (!isOwnerOrMember(project, userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this project");

        List<TeammateRequest> list;
        if (status != null && !status.isBlank()) {
            list = requestRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status.trim().toUpperCase());
        } else {
            list = requestRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ——— 7. Gap-aware suggest + send (convenience for chat tool) ———

    /**
     * Runs the full gap workflow: finds skill gaps → ranks candidates → returns
     * the top N suggestions with their matched skills. The chat tool can then
     * present these and the user picks who to send requests to.
     */
    public ProjectToolService.TeammateSuggestions suggestForProject(Long userId, Long projectId) {
        return projectToolService.findTeammates(userId, projectId);
    }

    // ——— helpers ———

    private void notifyCandidate(TeammateRequest req, Student requester, Project project, Student candidate) {
        try {
            notificationService.notify(candidate.getId(), "TEAMMATE_REQUEST",
                    requester.getName() + " wants you on " + project.getName(),
                    "Project: " + project.getName() + " — Skills needed: "
                            + (req.getMatchedSkills() != null ? req.getMatchedSkills() : "general")
                            + (req.getMessage() != null ? " — \"" + req.getMessage() + "\"" : ""),
                    "TEAMMATE_REQUEST", req.getId());
        } catch (Exception ignored) {}
    }

    private boolean isOwnerOrMember(Project p, Long studentId) {
        if (p.getOwner() != null && p.getOwner().getId().equals(studentId)) return true;
        return memberRepository.findByProjectId(p.getId()).stream()
                .anyMatch(m -> m.getStudent().getId().equals(studentId));
    }

    private boolean isAlreadyMember(Project p, Long studentId) {
        if (p.getOwner() != null && p.getOwner().getId().equals(studentId)) return true;
        return memberRepository.findByProjectId(p.getId()).stream()
                .anyMatch(m -> m.getStudent().getId().equals(studentId));
    }

    private RequestDto toDto(TeammateRequest req) {
        return new RequestDto(
                req.getId(),
                req.getProject() != null ? req.getProject().getId() : null,
                req.getProject() != null ? req.getProject().getName() : null,
                req.getRequester() != null ? req.getRequester().getId() : null,
                req.getRequester() != null ? req.getRequester().getName() : null,
                req.getCandidate() != null ? req.getCandidate().getId() : null,
                req.getCandidate() != null ? req.getCandidate().getName() : null,
                req.getStatus(),
                req.getMessage(),
                req.getMatchedSkills(),
                req.getCreatedAt() != null ? req.getCreatedAt().toString() : null,
                req.getRespondedAt() != null ? req.getRespondedAt().toString() : null);
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
