package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Roadmap;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RoadmapRepository;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/roadmaps")
public class RoadmapController {

    private final RoadmapRepository roadmapRepository;
    private final AlumniRepository alumniRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * POST /roadmaps {alumniId, title, targetRole, description, resources?}
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Long alumniId = toLong(body != null ? body.get("alumniId") : null);
        String title = getString(body, "title");
        String targetRole = getString(body, "targetRole");
        String description = getString(body, "description");
        if (alumniId == null) throw new IllegalArgumentException("alumniId required");
        if (title == null || title.length() < 3 || title.length() > 150)
            throw new IllegalArgumentException("Title must be 3-150 characters");
        if (targetRole == null || targetRole.isBlank())
            throw new IllegalArgumentException("targetRole required");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description required");
        if (description.length() > 5000)
            throw new IllegalArgumentException("description too long (max 5000)");

        String resourcesJson = validateResources(body != null ? body.get("resources") : null);

        Alumni alumni = alumniRepository.findById(alumniId)
                .orElseThrow(() -> new IllegalArgumentException("Alumni not found: " + alumniId));
        Roadmap r = new Roadmap(title, targetRole, description, alumni);
        r.setResources(resourcesJson);
        r = roadmapRepository.save(r);
        return toMap(r);
    }

    /** Resource kinds the frontend knows how to render an icon and label for. */
    private static final Set<String> RESOURCE_TYPES = Set.of("youtube", "blog", "pdf", "website");
    private static final int MAX_RESOURCES = 20;
    private static final int MAX_URL_LENGTH = 2048;

    /**
     * Validates an incoming `resources` value and returns it as the JSON string
     * we store. Returns null for "absent", so a roadmap without resources keeps
     * a null column rather than the string "null".
     *
     * The type is checked against a fixed set and the URL against http/https:
     * these strings are rendered straight into an <a href> on the client, so an
     * unchecked value could be a javascript: URL.
     */
    @SuppressWarnings("unchecked")
    private String validateResources(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof List<?> list))
            throw new IllegalArgumentException("resources must be an array");
        if (list.size() > MAX_RESOURCES)
            throw new IllegalArgumentException("Too many resources (max " + MAX_RESOURCES + ")");

        List<Map<String, String>> clean = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m))
                throw new IllegalArgumentException("Each resource must be an object with type and url");
            Object typeVal = m.get("type");
            Object urlVal = m.get("url");
            if (!(typeVal instanceof String) || !RESOURCE_TYPES.contains((String) typeVal))
                throw new IllegalArgumentException("Resource type must be one of " + RESOURCE_TYPES);
            if (!(urlVal instanceof String) || ((String) urlVal).isBlank())
                throw new IllegalArgumentException("Resource url is required");
            String type = (String) typeVal;
            String url = (String) urlVal;
            if (url.length() > MAX_URL_LENGTH)
                throw new IllegalArgumentException("Resource url too long (max " + MAX_URL_LENGTH + ")");
            String lower = url.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("http://") && !lower.startsWith("https://"))
                throw new IllegalArgumentException("Resource url must start with http:// or https://");

            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("url", url);
            Object labelVal = m.get("label");
            if (labelVal instanceof String) {
                String label = (String) labelVal;
                if (!label.isBlank())
                    entry.put("label", label.length() > 120 ? label.substring(0, 120) : label.trim());
            }
            clean.add(entry);
        }
        if (clean.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(clean);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not store resources: " + e.getMessage());
        }
    }

    /** Reads the stored JSON back into a list; a malformed column yields an empty list, not a 500. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readResources(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> toMap(Roadmap r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle());
        m.put("targetRole", r.getTargetRole());
        m.put("description", r.getDescription());
        m.put("resources", readResources(r.getResources()));
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
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v instanceof String) {
            String s = (String) v;
            if (!s.isBlank()) {
                return Long.valueOf(s.trim());
            }
        }
        return null;
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof String) {
            return (String) v;
        }
        return null;
    }
}