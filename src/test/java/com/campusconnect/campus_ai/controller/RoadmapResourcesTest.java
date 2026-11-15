package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Roadmap;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RoadmapRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The roadmap resource area was unrenderable for two separate reasons that
 * both had to be fixed: the Roadmap entity had no column to store resources
 * in, and the page declared a DTO shape the API never returned. These tests
 * pin the wire contract the page depends on.
 */
@WebMvcTest(RoadmapController.class)
class RoadmapResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoadmapRepository roadmapRepository;

    @MockitoBean
    private AlumniRepository alumniRepository;

    @MockitoBean
    private com.campusconnect.campus_ai.service.JwtService jwtService;

    @MockitoBean
    private com.campusconnect.campus_ai.repository.UserRepository userRepository;

    private Roadmap roadmapWith(String resourcesJson) {
        Alumni a = new Alumni("Rahul Sharma", "CSE", 2022, "Java, Spring Boot",
                "Senior Backend Engineer", "Google", "Built payment systems.",
                "https://linkedin.com/in/rahulsharma", "MENTORING");

        Roadmap r = new Roadmap("Backend Developer Roadmap", "Backend Engineer",
                "Start with Java basics, then Spring Boot.", a);
        r.setResources(resourcesJson);
        return r;
    }

    /** A stored resource list comes back as a real array, not a JSON string. */
    @Test
    void listReturnsResourcesAsParsedArray() throws Exception {
        String json = "[{\"type\":\"youtube\",\"url\":\"https://youtu.be/x\",\"label\":\"Spring Boot\"}]";
        when(roadmapRepository.findAll()).thenReturn(List.of(roadmapWith(json)));

        mockMvc.perform(get("/roadmaps").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resources").isArray())
                .andExpect(jsonPath("$[0].resources[0].type").value("youtube"))
                .andExpect(jsonPath("$[0].resources[0].url").value("https://youtu.be/x"))
                .andExpect(jsonPath("$[0].resources[0].label").value("Spring Boot"))
                // The fields the page used to invent must never appear.
                .andExpect(jsonPath("$[0].ctc").doesNotExist())
                .andExpect(jsonPath("$[0].requirements").doesNotExist());
    }

    /** Every roadmap seeded before the column existed has a null resources value. */
    @Test
    void listReturnsEmptyArrayWhenNoResources() throws Exception {
        when(roadmapRepository.findAll()).thenReturn(List.of(roadmapWith(null)));

        mockMvc.perform(get("/roadmaps").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resources").isArray())
                .andExpect(jsonPath("$[0].resources").isEmpty());
    }

    /** A malformed column degrades to an empty list rather than a 500. */
    @Test
    void malformedResourcesColumnDoesNotBreakTheList() throws Exception {
        when(roadmapRepository.findAll()).thenReturn(List.of(roadmapWith("{not json")));

        mockMvc.perform(get("/roadmaps").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resources").isEmpty());
    }

    @Test
    void createStoresValidResources() throws Exception {
        when(alumniRepository.findById(3L)).thenReturn(Optional.of(roadmapWith(null).getAlumni()));
        when(roadmapRepository.save(any(Roadmap.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "alumniId": 3,
                  "title": "Backend Developer Roadmap",
                  "targetRole": "Backend Engineer",
                  "description": "Start with Java basics.",
                  "resources": [
                    {"type": "website", "url": "https://roadmap.sh/backend", "label": "Roadmap"},
                    {"type": "pdf",   "url": "https://example.com/guide.pdf"}
                  ]
                }
                """;

        mockMvc.perform(post("/roadmaps").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources").isArray())
                .andExpect(jsonPath("$.resources.length()").value(2))
                .andExpect(jsonPath("$.resources[0].type").value("website"))
                .andExpect(jsonPath("$.resources[1].label").doesNotExist());
    }

    /**
     * These URLs render straight into an href, so anything that is not http(s)
     * has to be rejected at the boundary — a javascript: URL here would be
     * stored XSS.
     */
    @Test
    void createRejectsJavascriptUrl() throws Exception {
        String body = """
                {
                  "alumniId": 3,
                  "title": "Backend Developer Roadmap",
                  "targetRole": "Backend Engineer",
                  "description": "Start with Java basics.",
                  "resources": [{"type": "website", "url": "javascript:alert(1)"}]
                }
                """;

        mockMvc.perform(post("/roadmaps").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(roadmapRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownResourceType() throws Exception {
        String body = """
                {
                  "alumniId": 3,
                  "title": "Backend Developer Roadmap",
                  "targetRole": "Backend Engineer",
                  "description": "Start with Java basics.",
                  "resources": [{"type": "tiktok", "url": "https://example.com"}]
                }
                """;

        mockMvc.perform(post("/roadmaps").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(roadmapRepository, never()).save(any());
    }
}
