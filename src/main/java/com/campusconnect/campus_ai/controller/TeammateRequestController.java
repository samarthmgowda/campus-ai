package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ProjectToolService;
import com.campusconnect.campus_ai.service.TeammateRequestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/teammate-requests")
public class TeammateRequestController {

    private final TeammateRequestService service;

    public TeammateRequestController(TeammateRequestService service) {
        this.service = service;
    }

    /**
     * Send a teammate request to a candidate who fills a project's skill gaps.
     * X-Demo-User = requesterId
     */
    @PostMapping
    public TeammateRequestService.RequestDto send(
            @RequestHeader("X-Demo-User") Long requesterId,
            @RequestParam Long projectId,
            @RequestParam Long candidateId,
            @RequestParam(required = false) String message) {
        return service.send(requesterId, projectId, candidateId, message);
    }

    /**
     * Accept or reject an incoming teammate request.
     * X-Demo-User = candidateId
     */
    @PutMapping("/{requestId}/respond")
    public TeammateRequestService.RequestDto respond(
            @RequestHeader("X-Demo-User") Long candidateId,
            @PathVariable Long requestId,
            @RequestParam String action) {
        return service.respond(requestId, candidateId, action);
    }

    /**
     * Withdraw a pending request (requester or project owner only).
     * X-Demo-User = requesterId
     */
    @PutMapping("/{requestId}/withdraw")
    public TeammateRequestService.RequestDto withdraw(
            @RequestHeader("X-Demo-User") Long requesterId,
            @PathVariable Long requestId) {
        return service.withdraw(requestId, requesterId);
    }

    /**
     * List incoming requests for me. ?status=PENDING|ACCEPTED|REJECTED
     * X-Demo-User = candidateId
     */
    @GetMapping("/mine")
    public List<TeammateRequestService.RequestDto> getMyRequests(
            @RequestHeader("X-Demo-User") Long candidateId,
            @RequestParam(required = false) String status) {
        return service.getMyRequests(candidateId, status);
    }

    /**
     * List requests I sent.
     * X-Demo-User = requesterId
     */
    @GetMapping("/sent")
    public List<TeammateRequestService.RequestDto> getSentRequests(
            @RequestHeader("X-Demo-User") Long requesterId) {
        return service.getSentRequests(requesterId);
    }

    /**
     * List all requests for a project (owner or member only).
     * X-Demo-User = userId
     */
    @GetMapping("/project/{projectId}")
    public List<TeammateRequestService.RequestDto> getProjectRequests(
            @RequestHeader("X-Demo-User") Long userId,
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        return service.getProjectRequests(projectId, userId, status);
    }

    /**
     * Convenience: get gap analysis + ranked candidates for a project.
     * X-Demo-User = userId
     */
    @GetMapping("/project/{projectId}/suggestions")
    public ProjectToolService.TeammateSuggestions suggest(
            @RequestHeader("X-Demo-User") Long userId,
            @PathVariable Long projectId) {
        return service.suggestForProject(userId, projectId);
    }
}
