package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ProjectToolService;
import com.campusconnect.campus_ai.service.TeammateMatchingService;
import com.campusconnect.campus_ai.service.ToolRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TEMPORARY - for testing tools
 */
@RestController
@RequestMapping("/dev/tools")
public class DevToolsController {

    private final ProjectToolService projectToolService;
    private final TeammateMatchingService teammateMatchingService;
    private final ToolRegistry toolRegistry;

    public DevToolsController(ProjectToolService projectToolService,
                              TeammateMatchingService teammateMatchingService,
                              ToolRegistry toolRegistry) {
        this.projectToolService = projectToolService;
        this.teammateMatchingService = teammateMatchingService;
        this.toolRegistry = toolRegistry;
    }

    @GetMapping("/teammates")
    public ProjectToolService.TeammateSuggestions teammates(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam Long projectId) {
        Long userId;
        try {
            userId = header == null ? null : Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown demo user");
        }
        if (userId == null) throw new IllegalArgumentException("Unknown demo user");
        return projectToolService.findTeammates(userId, projectId);
    }

    /**
     * Weighted teammate ranking (research track): S = 0.50*SkillMatch +
     * 0.20*ProjectInterest + 0.20*Availability + 0.10*ComplementarySkills,
     * optional bge-m3 semantic blend. Same membership guards as /teammates.
     * GET /dev/tools/teammates-weighted?projectId=&availableOnly=true&semantic=false
     */
    @GetMapping("/teammates-weighted")
    public TeammateMatchingService.WeightedSuggestions teammatesWeighted(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "true") boolean availableOnly,
            @RequestParam(defaultValue = "false") boolean semantic) {
        Long userId;
        try {
            userId = header == null ? null : Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown demo user");
        }
        if (userId == null) throw new IllegalArgumentException("Unknown demo user");
        return teammateMatchingService.rankForProject(userId, projectId, availableOnly, semantic);
    }

    // TEMPORARY - for testing searchStudents availableOnly flag
    @GetMapping("/search")
    public List<ProjectToolService.CandidateMatch> search(
            @RequestParam String skills,
            @RequestParam(defaultValue = "true") boolean availableOnly) {
        List<String> skillList = List.of(skills.split(","));
        return projectToolService.searchStudents(skillList, availableOnly, List.of());
    }

    // TEMPORARY - expose tool registry for agent handoff verification
    @GetMapping("/registry")
    public List<ToolRegistry.ToolSpec> registry() {
        return toolRegistry.allToolSpecs();
    }
}