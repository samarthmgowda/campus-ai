package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DemoIdentity;
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
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam Long projectId,
            @RequestParam Long candidateId,
            @RequestParam(required = false) String message) {
        Long requesterId = DemoIdentity.requireId(header);
        return service.send(requesterId, projectId, candidateId, message);
    }

    /**
     * Accept or reject an incoming teammate request.
     * X-Demo-User = candidateId
     */
    @PutMapping("/{requestId}/respond")
    public TeammateRequestService.RequestDto respond(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @PathVariable Long requestId,
            @RequestParam String action) {
        Long candidateId = DemoIdentity.requireId(header);
        return service.respond(requestId, candidateId, action);
    }

    /**
     * Withdraw a pending request (requester or project owner only).
     * X-Demo-User = requesterId
     */
    @PutMapping("/{requestId}/withdraw")
    public TeammateRequestService.RequestDto withdraw(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @PathVariable Long requestId) {
        Long requesterId = DemoIdentity.requireId(header);
        return service.withdraw(requestId, requesterId);
    }

    /**
     * List incoming requests for me. ?status=PENDING|ACCEPTED|REJECTED
     * X-Demo-User = candidateId
     */
    @GetMapping("/mine")
    public List<TeammateRequestService.RequestDto> getMyRequests(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) String status) {
        Long candidateId = DemoIdentity.requireId(header);
        return service.getMyRequests(candidateId, status);
    }

    /**
     * List requests I sent.
     * X-Demo-User = requesterId
     */
    @GetMapping("/sent")
    public List<TeammateRequestService.RequestDto> getSentRequests(
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long requesterId = DemoIdentity.requireId(header);
        return service.getSentRequests(requesterId);
    }

    /**
     * List all requests for a project (owner or member only).
     * X-Demo-User = userId
     */
    @GetMapping("/project/{projectId}")
    public List<TeammateRequestService.RequestDto> getProjectRequests(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        Long userId = DemoIdentity.requireId(header);
        return service.getProjectRequests(projectId, userId, status);
    }

    /**
     * Convenience: get gap analysis + ranked candidates for a project.
     * X-Demo-User = userId
     */
    @GetMapping("/project/{projectId}/suggestions")
    public ProjectToolService.TeammateSuggestions suggest(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @PathVariable Long projectId) {
        Long userId = DemoIdentity.requireId(header);
        return service.suggestForProject(userId, projectId);
    }
}
