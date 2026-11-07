package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AuthContext;
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
 * acting user = requesterId
     */
    @PostMapping
    public TeammateRequestService.RequestDto send(
            @RequestParam Long projectId,
            @RequestParam Long candidateId,
            @RequestParam(required = false) String message) {
        Long requesterId = AuthContext.id();
        return service.send(requesterId, projectId, candidateId, message);
    }

    /**
     * Accept or reject an incoming teammate request.
 * acting user = candidateId
     */
    @PutMapping("/{requestId}/respond")
    public TeammateRequestService.RequestDto respond(
            @PathVariable Long requestId,
            @RequestParam String action) {
        Long candidateId = AuthContext.id();
        return service.respond(requestId, candidateId, action);
    }

    /**
     * Withdraw a pending request (requester or project owner only).
 * acting user = requesterId
     */
    @PutMapping("/{requestId}/withdraw")
    public TeammateRequestService.RequestDto withdraw(
            @PathVariable Long requestId) {
        Long requesterId = AuthContext.id();
        return service.withdraw(requestId, requesterId);
    }

    /**
     * List incoming requests for me. ?status=PENDING|ACCEPTED|REJECTED
 * acting user = candidateId
     */
    @GetMapping("/mine")
    public List<TeammateRequestService.RequestDto> getMyRequests(
            @RequestParam(required = false) String status) {
        Long candidateId = AuthContext.id();
        return service.getMyRequests(candidateId, status);
    }

    /**
     * List requests I sent.
     */
    @GetMapping("/sent")
    public List<TeammateRequestService.RequestDto> getSentRequests() {
        Long requesterId = AuthContext.id();
        return service.getSentRequests(requesterId);
    }

    /**
     * List all requests for a project (owner or member only).
     */
    @GetMapping("/project/{projectId}")
    public List<TeammateRequestService.RequestDto> getProjectRequests(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        Long userId = AuthContext.id();
        return service.getProjectRequests(projectId, userId, status);
    }

    /**
     * Convenience: get gap analysis + ranked candidates for a project.
 * acting user = userId
     */
    @GetMapping("/project/{projectId}/suggestions")
    public ProjectToolService.TeammateSuggestions suggest(
            @PathVariable Long projectId) {
        Long userId = AuthContext.id();
        return service.suggestForProject(userId, projectId);
    }
}
