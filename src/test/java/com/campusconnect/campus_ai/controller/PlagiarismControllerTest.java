package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.PlagiarismDetectionService;
import com.campusconnect.campus_ai.service.PlagiarismDetectionService.ScanResult;
import com.campusconnect.campus_ai.service.PlagiarismDetectionService.SourceMatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pins the wire contract the Library's originality panel depends on.
 */
@WebMvcTest(PlagiarismController.class)
class PlagiarismControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlagiarismDetectionService plagiarismDetectionService;

    @MockitoBean
    private com.campusconnect.campus_ai.service.PlagiarismService plagiarismService;

    @MockitoBean
    private com.campusconnect.campus_ai.service.JwtService jwtService;

    @MockitoBean
    private com.campusconnect.campus_ai.repository.UserRepository userRepository;

    private static ScanResult highMatch() {
        return new ScanResult("HIGH", 0.91, 42, 63,
                "Close match with \"Academic Handbook\".",
                List.of(new SourceMatch(10L, "Academic Handbook", "Attendance", 4,
                        0.91, 0.95, 0.85)));
    }

    @Test
    void scanReturnsRankedMatches() throws Exception {
        when(plagiarismDetectionService.scan(anyString())).thenReturn(highMatch());

        mockMvc.perform(post("/evaluation/plagiarism/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Students must maintain seventy five percent attendance.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("HIGH"))
                .andExpect(jsonPath("$.topScore").value(0.91))
                .andExpect(jsonPath("$.scannedChunks").value(42))
                .andExpect(jsonPath("$.submissionWords").value(63))
                .andExpect(jsonPath("$.summary").isString())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.matches[0].documentId").value(10))
                .andExpect(jsonPath("$.matches[0].documentTitle").value("Academic Handbook"))
                .andExpect(jsonPath("$.matches[0].section").value("Attendance"))
                .andExpect(jsonPath("$.matches[0].pageNumber").value(4))
                .andExpect(jsonPath("$.matches[0].wordCoverage").value(0.95))
                .andExpect(jsonPath("$.matches[0].phraseCoverage").value(0.85));
    }

    @Test
    void insufficientTextIsA200WithItsOwnVerdict() throws Exception {
        when(plagiarismDetectionService.scan(anyString())).thenReturn(
                new ScanResult("INSUFFICIENT", 0.0, 0, 2, "Add more text.", List.of()));

        mockMvc.perform(post("/evaluation/plagiarism/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"spring boot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.matches").isEmpty());
    }

    @Test
    void blankTextIs400AndNeverReachesTheService() throws Exception {
        for (String body : new String[]{"{\"text\":\"\"}", "{\"text\":\"   \"}", "{\"text\":null}", "{}", ""}) {
            mockMvc.perform(post("/evaluation/plagiarism/scan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isString());
        }
        verifyNoInteractions(plagiarismDetectionService);
    }

    /**
     * The scan is linear in corpus size, so an unbounded body is a cheap way to
     * tie up a request thread. The limit has to be enforced before the service
     * sees the text.
     */
    @Test
    void oversizedTextIs400AndNeverReachesTheService() throws Exception {
        String huge = "word ".repeat(10_001); // 50_005 chars
        String body = "{\"text\":\"" + huge + "\"}";

        mockMvc.perform(post("/evaluation/plagiarism/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("limit")));

        verifyNoInteractions(plagiarismDetectionService);
    }

    @Test
    void crossScanReturnsSubmissionMatches() throws Exception {
        when(plagiarismService.scanAgainstAllSubmissions(anyString())).thenReturn(
                new com.campusconnect.campus_ai.service.PlagiarismService.CrossScanResult(
                        "HIGH", 0.88, 3, 41, "Close match with \"Placement report\".",
                        List.of(new com.campusconnect.campus_ai.service.PlagiarismService.SubmissionMatch(
                                7L, "Placement report", "aarav@campus.edu", "SUBMITTED",
                                0.88, 0.90, 0.85))));

        mockMvc.perform(post("/evaluation/plagiarism/scan-all-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Students apply to dozens of companies and still struggle.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("HIGH"))
                .andExpect(jsonPath("$.topScore").value(0.88))
                // The field names differ from the library scan on purpose: a peer
                // submission has no section or page, and does have an author.
                .andExpect(jsonPath("$.scannedSubmissions").value(3))
                .andExpect(jsonPath("$.matches[0].submissionId").value(7))
                .andExpect(jsonPath("$.matches[0].title").value("Placement report"))
                .andExpect(jsonPath("$.matches[0].userId").value("aarav@campus.edu"))
                .andExpect(jsonPath("$.matches[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.matches[0].wordCoverage").value(0.90));
    }

    @Test
    void blankTextOnTheCrossScanIs400() throws Exception {
        mockMvc.perform(post("/evaluation/plagiarism/scan-all-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        verifyNoInteractions(plagiarismService);
    }

    @Test
    void textAtTheLimitIsAccepted() throws Exception {
        when(plagiarismDetectionService.scan(anyString())).thenReturn(highMatch());
        // Exactly MAX_CHARS: 'a' * 50_000. Built here rather than inline so the
        // boundary is explicit.
        String atLimit = "a".repeat(50_000);

        mockMvc.perform(post("/evaluation/plagiarism/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + atLimit + "\"}"))
                .andExpect(status().isOk());
    }
}
