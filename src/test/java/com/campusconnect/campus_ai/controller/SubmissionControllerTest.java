package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.ProjectSubmission;
import com.campusconnect.campus_ai.repository.ProjectSubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The corpus endpoint the cross-submission originality check reads from.
 *
 * The failure that matters here is storing a body that can never be checked
 * (blank, or over the scan's own limit) — that would fill the corpus with rows
 * that silently make every future scan less accurate.
 */
@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectSubmissionRepository submissionRepository;

    @MockitoBean
    private com.campusconnect.campus_ai.service.JwtService jwtService;

    @MockitoBean
    private com.campusconnect.campus_ai.repository.UserRepository userRepository;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static ProjectSubmission stored(long id, String title, String text) {
        ProjectSubmission s = new ProjectSubmission();
        s.setId(id);
        s.setTitle(title);
        s.setUserId("aarav@campus.edu");
        s.setStatus("SUBMITTED");
        s.setSubmissionText(text);
        s.setCreatedAt(LocalDateTime.now().minusDays(1));
        return s;
    }

    @Test
    void listReturnsTheStoredCorpusNewestFirst() throws Exception {
        when(submissionRepository.findAll()).thenReturn(List.of(
                stored(1L, "Older report", "one two three"),
                stored(2L, "Newer report", "four five six")));

        mockMvc.perform(get("/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Newer report"))
                .andExpect(jsonPath("$[0].words").value(3))
                .andExpect(jsonPath("$[0].excerpt").value("four five six"))
                .andExpect(jsonPath("$[1].title").value("Older report"));
    }

    @Test
    void storingABodyAddsItToTheCorpus() throws Exception {
        when(submissionRepository.save(any(ProjectSubmission.class))).thenAnswer(inv -> {
            ProjectSubmission s = inv.getArgument(0);
            s.setId(7L);
            return s;
        });
        TestAuth.asAdmin();

        mockMvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Data Structures — final report\",\"text\":\"one two three four\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Data Structures — final report"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.words").value(4));
    }

    @Test
    void aMissingTitleGetsAFallbackRatherThanAnEmptyRow() throws Exception {
        when(submissionRepository.save(any(ProjectSubmission.class))).thenAnswer(inv -> {
            ProjectSubmission s = inv.getArgument(0);
            s.setId(8L);
            return s;
        });
        TestAuth.asAdmin();

        mockMvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"one two three four\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Untitled submission"));
    }

    @Test
    void blankTextIs400AndNeverReachesTheRepository() throws Exception {
        TestAuth.asAdmin();

        for (String body : new String[]{
                "{\"title\":\"x\",\"text\":\"\"}",
                "{\"title\":\"x\",\"text\":\"   \"}",
                "{\"title\":\"x\",\"text\":null}",
                "{\"title\":\"x\"}",
                ""}) {
            mockMvc.perform(post("/submissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isString());
        }
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void oversizedTextIs400BecauseItCouldNeverBeScanned() throws Exception {
        TestAuth.asAdmin();
        String huge = "word ".repeat(10_001);

        mockMvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + huge + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("limit")));

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void anExplicitSubmitterIsRecorded() throws Exception {
        when(submissionRepository.save(any(ProjectSubmission.class))).thenAnswer(inv -> {
            ProjectSubmission s = inv.getArgument(0);
            s.setId(9L);
            return s;
        });
        TestAuth.asAdmin();

        mockMvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"one two three four\",\"userId\":\"rohan.verma@campus.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("rohan.verma@campus.edu"));
    }

    /** Anonymous callers are refused by AuthContext before anything is written. */
    @Test
    void anonymousStoreIs401() throws Exception {
        mockMvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"one two three four\"}"))
                .andExpect(status().isUnauthorized());
    }
}
