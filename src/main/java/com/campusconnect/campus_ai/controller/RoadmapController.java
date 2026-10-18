package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Roadmap;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RoadmapRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/roadmaps")
public class RoadmapController {

    private final RoadmapRepository roadmapRepository;
    private final AlumniRepository alumniRepository;

    public RoadmapController(RoadmapRepository roadmapRepository, AlumniRepository alumniRepository) {
        this.roadmapRepository = roadmapRepository;
        this.alumniRepository = alumniRepository;
    }

    /**
     * All roadmaps, optionally filtered by role keyword.
     * GET /roadmaps?role=backend
     */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String role) {
        List<Roadmap> all = roadmapRepository.findAll();
        return all.stream()
                .filter(r -> role == null || role.isBlank()
                        || (r.getTargetRole() != null && r.getTargetRole().toLowerCase().contains(role.trim().toLowerCase()))
                        || (r.getTitle() != null && r.getTitle().toLowerCase().contains(role.trim().toLowerCase())))
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Roadmap detail.
     * GET /roadmaps/{id}
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Roadmap r = roadmapRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Roadmap not found: " + id));
        return toMap(r);
    }

    /**
     * Alumni publishes a roadmap. The alumni identity comes from the body
     * (alumniId) so alumni can post without a student demo header.
     * POST /roadmaps {alumniId, title, targetRole, description}
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Long alumniId = toLong(body != null ? body.get("alumniId") : null);
        String title = body != null && body.get("title") instanceof String s ? s.trim() : null;
        String targetRole = body != null && body.get("targetRole") instanceof String s ? s.trim() : null;
        String description = body != null && body.get("description") instanceof String s ? s.trim() : null;
        if (alumniId == null) throw new IllegalArgumentException("alumniId required");
        if (title == null || title.length() < 3 || title.length() > 150)
            throw new IllegalArgumentException("Title must be 3-150 characters");
        if (targetRole == null || targetRole.isBlank())
            throw new IllegalArgumentException("targetRole required");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description required");
        if (description.length() > 5000)
            throw new IllegalArgumentException("description too long (max 5000)");

        Alumni alumni = alumniRepository.findById(alumniId)
                .orElseThrow(() -> new IllegalArgumentException("Alumni not found: " + alumniId));
        Roadmap r = new Roadmap(title, targetRole, description, alumni);
        r = roadmapRepository.save(r);
        return toMap(r);
    }

    private Map<String, Object> toMap(Roadmap r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle());
        m.put("targetRole", r.getTargetRole());
        m.put("description", r.getDescription());
        if (r.getAlumni() != null) {
            m.put("alumniId", r.getAlumni().getId());
            m.put("alumniName", r.getAlumni().getName());
            m.put("alumniRole", r.getAlumni().getCurrentRole());
            m.put("alumniCompany", r.getAlumni().getCurrentCompany());
        }
        return m;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) return Long.valueOf(s.trim());
        return null;
    }
}
