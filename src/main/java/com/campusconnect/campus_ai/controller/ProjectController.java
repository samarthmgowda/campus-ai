package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.ProjectMember;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.service.CurrentUserService;
import com.campusconnect.campus_ai.service.ProjectToolService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final CurrentUserService currentUserService;
    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectToolService projectToolService;

    public ProjectController(CurrentUserService currentUserService,
                             ProjectMemberRepository memberRepository,
                             ProjectRepository projectRepository,
                             ProjectToolService projectToolService) {
        this.currentUserService = currentUserService;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.projectToolService = projectToolService;
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine() {
        Long userId;
        try {
            userId = AuthContext.idOrNull();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown demo user");
        }
        currentUserService.requireUser(userId);

        // ACTIVE projects where user is a member (owner is also a member)
        List<ProjectMember> memberships = memberRepository.findByStudentId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectMember pm : memberships) {
            Project p = pm.getProject();
            if (p == null) continue;
            // Need to fetch fresh to ensure members loaded; repository already has it
            // Filter to ACTIVE only
            if (!"ACTIVE".equalsIgnoreCase(p.getStatus())) continue;
            if ("HIDDEN".equalsIgnoreCase(p.getStatus())) continue;
            // Avoid duplicates if user appears twice (should not happen)
            if (result.stream().anyMatch(m -> m.get("id").equals(p.getId()))) continue;

            List<ProjectMember> members = memberRepository.findByProjectId(p.getId());
            List<Map<String, Object>> memberDtos = members.stream().map(m -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("studentId", m.getStudent().getId());
                dto.put("name", m.getStudent().getName());
                dto.put("role", m.getRole());
                return dto;
            }).collect(Collectors.toList());

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", p.getId());
            dto.put("name", p.getName());
            dto.put("description", p.getDescription());
            dto.put("domain", p.getDomain());
            dto.put("requiredSkills", p.getRequiredSkills());
            dto.put("status", p.getStatus());
            dto.put("ownerName", p.getOwner() != null ? p.getOwner().getName() : null);
            dto.put("members", memberDtos);
            result.add(dto);
        }
        return result;
    }

    @PostMapping
    public Map<String, Object> create(                                      @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.id();
        String name = body != null && body.get("name") instanceof String s ? s : null;
        String description = body != null && body.get("description") instanceof String s ? s : null;
        String domain = body != null && body.get("domain") instanceof String s ? s : null;
        String requiredSkills = body != null && body.get("requiredSkills") instanceof String s ? s : null;
        var summary = projectToolService.createProject(userId, name, description, domain, requiredSkills);
        return Map.of("id", summary.id(), "name", summary.name(), "domain", summary.domain() != null ? summary.domain() : "", "requiredSkills", summary.requiredSkills() != null ? summary.requiredSkills() : "", "status", summary.status(), "ownerName", summary.ownerName() != null ? summary.ownerName() : "");
    }

    @PostMapping("/{projectId}/members")
    public Map<String, Object> addMember(                                         @PathVariable Long projectId,
                                         @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.id();
        Long studentId = body != null ? toLong(body.get("studentId")) : null;
        if (studentId == null && body != null && body.get("memberId") != null) studentId = toLong(body.get("memberId"));
        if (studentId == null) throw new IllegalArgumentException("studentId required");
        var m = projectToolService.addMember(userId, projectId, studentId);
        return Map.of("studentId", m.studentId(), "name", m.name(), "role", m.role());
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
