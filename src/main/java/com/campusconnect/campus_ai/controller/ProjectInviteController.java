package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ProjectInviteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ProjectInviteController {

    private final ProjectInviteService inviteService;

    public ProjectInviteController(ProjectInviteService inviteService) {
        this.inviteService = inviteService;
    }

    /** POST /projects/{projectId}/invite {inviteeId} with X-Demo-User (inviter) */
    @PostMapping("/projects/{projectId}/invite")
    public ProjectInviteService.InviteDto invite(
            @PathVariable Long projectId,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody(required = false) Map<String, Object> body) {
        Long inviterId = requireHeader(header, body);
        Long inviteeId = body != null ? toLong(body.get("inviteeId")) : null;
        if (inviteeId == null) inviteeId = body != null ? toLong(body.get("studentId")) : null;
        if (inviteeId == null) inviteeId = body != null ? toLong(body.get("peerId")) : null;
        if (inviteeId == null) throw new IllegalArgumentException("inviteeId required");
        return inviteService.invite(projectId, inviterId, inviteeId);
    }

    /** POST /project-invites/{inviteId}/respond {action: ACCEPT|REJECT} with X-Demo-User (invitee) */
    @PostMapping("/project-invites/{inviteId}/respond")
    public ProjectInviteService.InviteDto respond(
            @PathVariable Long inviteId,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody(required = false) Map<String, Object> body) {
        Long inviteeId = requireHeader(header, body);
        String action = body != null && body.get("action") instanceof String s ? s : (body != null && body.get("status") instanceof String s2 ? s2 : "ACCEPT");
        return inviteService.respond(inviteId, inviteeId, action);
    }

    /** POST /project-invites/{inviteId}/withdraw with X-Demo-User (inviter/owner) */
    @PostMapping("/project-invites/{inviteId}/withdraw")
    public Map<String, Object> withdraw(
            @PathVariable Long inviteId,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody(required = false) Map<String, Object> body) {
        Long inviterId = requireHeader(header, body);
        return inviteService.withdraw(inviteId, inviterId);
    }

    /** GET /project-invites/my?status=PENDING with X-Demo-User (invitee inbox) */
    @GetMapping("/project-invites/my")
    public List<ProjectInviteService.InviteDto> myInvites(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) String status) {
        Long inviteeId = requireHeader(header, null);
        return inviteService.getMyInvites(inviteeId, status);
    }

    /** GET /projects/{projectId}/invites?status=PENDING */
    @GetMapping("/projects/{projectId}/invites")
    public List<ProjectInviteService.InviteDto> projectInvites(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        return inviteService.getProjectInvites(projectId, status);
    }

    private Long requireHeader(String header, Map<String, Object> body) {
        if (header != null && !header.isBlank()) try { return Long.valueOf(header.trim()); } catch (NumberFormatException ignored) {}
        if (body != null) {
            Object v = body.get("inviterId");
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
            Object v2 = body.get("X-Demo-User");
            if (v2 instanceof Number n) return n.longValue();
            if (v2 instanceof String s2 && !s2.isBlank()) try { return Long.valueOf(s2.trim()); } catch (NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException("X-Demo-User header required");
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
