package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ProjectInviteService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ProjectInviteController {

    private final ProjectInviteService inviteService;

    public ProjectInviteController(ProjectInviteService inviteService) {
        this.inviteService = inviteService;
    }

 /** POST /projects/{projectId}/invite {inviteeId} (inviter) */
    @PostMapping("/projects/{projectId}/invite")
    public ProjectInviteService.InviteDto invite(
            @PathVariable Long projectId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long inviterId = AuthContext.id();
        Long inviteeId = body != null ? toLong(body.get("inviteeId")) : null;
        if (inviteeId == null) inviteeId = body != null ? toLong(body.get("studentId")) : null;
        if (inviteeId == null) inviteeId = body != null ? toLong(body.get("peerId")) : null;
        if (inviteeId == null) throw new IllegalArgumentException("inviteeId required");
        return inviteService.invite(projectId, inviterId, inviteeId);
    }

 /** POST /project-invites/{inviteId}/respond {action: ACCEPT|REJECT} (invitee) */
    @PostMapping("/project-invites/{inviteId}/respond")
    public ProjectInviteService.InviteDto respond(
            @PathVariable Long inviteId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long inviteeId = AuthContext.id();
        String action = body != null && body.get("action") instanceof String s ? s : (body != null && body.get("status") instanceof String s2 ? s2 : "ACCEPT");
        return inviteService.respond(inviteId, inviteeId, action);
    }

 /** POST /project-invites/{inviteId}/withdraw (inviter/owner) */
    @PostMapping("/project-invites/{inviteId}/withdraw")
    public Map<String, Object> withdraw(
            @PathVariable Long inviteId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long inviterId = AuthContext.id();
        return inviteService.withdraw(inviteId, inviterId);
    }

 /** GET /project-invites/my?status=PENDING (invitee inbox) */
    @GetMapping("/project-invites/my")
    public List<ProjectInviteService.InviteDto> myInvites(
            @RequestParam(required = false) String status) {
        Long inviteeId = AuthContext.id();
        return inviteService.getMyInvites(inviteeId, status);
    }

    /** GET /projects/{projectId}/invites?status=PENDING */
    @GetMapping("/projects/{projectId}/invites")
    public List<ProjectInviteService.InviteDto> projectInvites(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        return inviteService.getProjectInvites(projectId, status);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
