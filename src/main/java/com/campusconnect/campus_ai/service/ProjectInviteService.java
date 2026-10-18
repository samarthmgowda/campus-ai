package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.ProjectInvite;
import com.campusconnect.campus_ai.entity.ProjectMember;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ProjectInviteRepository;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectInviteService {

    private final ProjectInviteRepository inviteRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;

    public ProjectInviteService(ProjectInviteRepository inviteRepository,
                                ProjectRepository projectRepository,
                                ProjectMemberRepository memberRepository,
                                StudentRepository studentRepository,
                                NotificationService notificationService) {
        this.inviteRepository = inviteRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.studentRepository = studentRepository;
        this.notificationService = notificationService;
    }

    public InviteDto invite(Long projectId, Long inviterId, Long inviteeId) {
        if (projectId == null || inviterId == null || inviteeId == null)
            throw new IllegalArgumentException("projectId, inviterId and inviteeId required");
        if (inviterId.equals(inviteeId)) throw new IllegalArgumentException("Cannot invite yourself");
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        Student inviter = studentRepository.findById(inviterId)
                .orElseThrow(() -> new IllegalArgumentException("Inviter not found: " + inviterId));
        Student invitee = studentRepository.findById(inviteeId)
                .orElseThrow(() -> new IllegalArgumentException("Invitee not found: " + inviteeId));
        if (!isOwnerOrMember(project, inviterId)) throw new IllegalArgumentException("Inviter is not a member of this project");
        // already a member?
        if (isAlreadyMember(project, inviteeId)) throw new IllegalArgumentException(invitee.getName() + " is already a member of " + project.getName());
        // existing pending invite
        var existing = inviteRepository.findByProjectIdAndInviteeId(projectId, inviteeId);
        if (existing.isPresent() && "PENDING".equals(existing.get().getStatus())) {
            return toDto(existing.get());
        }
        // if previously rejected/accepted, reset to pending
        if (existing.isPresent()) {
            existing.get().setStatus("PENDING");
            ProjectInvite saved = inviteRepository.save(existing.get());
            try {
                notificationService.notify(inviteeId, "INVITE", inviter.getName() + " invited you to " + project.getName(),
                        "Project: " + project.getName() + " (" + project.getDomain() + ")", "PROJECT_INVITE", saved.getId());
            } catch (Exception ignored) {}
            return toDto(saved);
        }
        ProjectInvite inv = new ProjectInvite(project, inviter, invitee);
        inv = inviteRepository.save(inv);
        try {
            notificationService.notify(inviteeId, "INVITE", inviter.getName() + " invited you to " + project.getName(),
                    "Project: " + project.getName() + " (" + project.getDomain() + ")", "PROJECT_INVITE", inv.getId());
        } catch (Exception ignored) {}
        return toDto(inv);
    }

    public InviteDto respond(Long inviteId, Long inviteeId, String action) {
        if (inviteId == null || inviteeId == null || action == null)
            throw new IllegalArgumentException("inviteId, inviteeId and action required");
        ProjectInvite inv = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found: " + inviteId));
        if (!inv.getInvitee().getId().equals(inviteeId)) throw new IllegalArgumentException("Not your invite");
        if (!"PENDING".equals(inv.getStatus())) throw new IllegalArgumentException("Invite already " + inv.getStatus().toLowerCase());
        String act = action.trim().toUpperCase();
        if (!act.equals("ACCEPT") && !act.equals("REJECT") && !act.equals("ACCEPTED") && !act.equals("REJECTED")) {
            throw new IllegalArgumentException("Action must be ACCEPT or REJECT");
        }
        boolean accept = act.startsWith("ACCEPT");
        inv.setStatus(accept ? "ACCEPTED" : "REJECTED");
        inv = inviteRepository.save(inv);
        if (accept) {
            if (!isAlreadyMember(inv.getProject(), inviteeId)) {
                memberRepository.save(new ProjectMember(inv.getProject(), inv.getInvitee(), "MEMBER"));
            }
            try {
                notificationService.notifyOther(inv.getInviter().getId(), inviteeId, "INVITE",
                        inv.getInvitee().getName() + " accepted your invite to " + inv.getProject().getName(),
                        "Project: " + inv.getProject().getName(), "PROJECT_INVITE", inv.getId());
            } catch (Exception ignored) {}
        }
        return toDto(inv);
    }

    public List<InviteDto> getMyInvites(Long inviteeId, String status) {
        studentRepository.findById(inviteeId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + inviteeId));
        List<ProjectInvite> list;
        if (status != null && !status.isBlank()) {
            list = inviteRepository.findByInviteeIdAndStatusOrderByCreatedAtDesc(inviteeId, status.trim().toUpperCase());
        } else {
            list = inviteRepository.findByInviteeIdOrderByCreatedAtDesc(inviteeId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<InviteDto> getProjectInvites(Long projectId, String status) {
        projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        String s = status != null && !status.isBlank() ? status.trim().toUpperCase() : "PENDING";
        return inviteRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, s).stream().map(this::toDto).collect(Collectors.toList());
    }

    public Map<String, Object> withdraw(Long inviteId, Long inviterId) {
        ProjectInvite inv = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found: " + inviteId));
        if (!inv.getInviter().getId().equals(inviterId) && !inv.getProject().getOwner().getId().equals(inviterId)) {
            throw new IllegalArgumentException("Only the inviter can withdraw");
        }
        if (!"PENDING".equals(inv.getStatus())) throw new IllegalArgumentException("Invite already " + inv.getStatus().toLowerCase());
        inviteRepository.delete(inv);
        return Map.of("id", inviteId, "status", "withdrawn");
    }

    public record InviteDto(Long id, Long projectId, String projectName, Long inviterId, String inviterName, Long inviteeId, String inviteeName, String status, String createdAt) {}

    private InviteDto toDto(ProjectInvite inv) {
        return new InviteDto(inv.getId(),
                inv.getProject() != null ? inv.getProject().getId() : null,
                inv.getProject() != null ? inv.getProject().getName() : null,
                inv.getInviter() != null ? inv.getInviter().getId() : null,
                inv.getInviter() != null ? inv.getInviter().getName() : null,
                inv.getInvitee() != null ? inv.getInvitee().getId() : null,
                inv.getInvitee() != null ? inv.getInvitee().getName() : null,
                inv.getStatus(),
                inv.getCreatedAt() != null ? inv.getCreatedAt().toString() : null);
    }

    private boolean isOwnerOrMember(Project p, Long studentId) {
        if (p.getOwner() != null && p.getOwner().getId().equals(studentId)) return true;
        return memberRepository.findByProjectId(p.getId()).stream().anyMatch(m -> m.getStudent().getId().equals(studentId));
    }

    private boolean isAlreadyMember(Project p, Long studentId) {
        if (p.getOwner() != null && p.getOwner().getId().equals(studentId)) return true;
        return memberRepository.findByProjectId(p.getId()).stream().anyMatch(m -> m.getStudent().getId().equals(studentId));
    }
}
