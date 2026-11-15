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

    /**
     * The cross-scan embeds the pasted text before comparing. Stubbed to look
     * like Ollama is down (the default: no stub → null) unless a test says
     * otherwise, because "the check still answers without the model server" is
     * the property that must not regress.
     */
    @MockitoBean
    private com.campusconnect.campus_ai.service.EmbeddingService embeddingService;

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
        when(plagiarismService.scanAgainstAllSubmissions(anyString(), any())).thenReturn(
                new com.campusconnect.campus_ai.service.PlagiarismService.CrossScanResult(
                        "HIGH", 0.88, 3, 41, 3, "Close match with \"Placement report\".",
                        List.of(new com.campusconnect.campus_ai.service.PlagiarismService.SubmissionMatch(
                                7L, "Placement report", "aarav@campus.edu", "SUBMITTED",
                                0.88, 0.90, 0.85, 0.97))));

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

    /**
     * The whole point of the embedding column: when Ollama answers, the vector
     * reaches the service so a paraphrase can be caught.
     */
    @Test
    void theCrossScanPassesTheEmbeddingToTheService() throws Exception {
        float[] vector = new float[1024];
        vector[0] = 1.0f;
        when(embeddingService.embed(anyString())).thenReturn(vector);
        when(plagiarismService.scanAgainstAllSubmissions(anyString(), any())).thenReturn(
                new com.campusconnect.campus_ai.service.PlagiarismService.CrossScanResult(
                        "HIGH", 0.82, 2, 40, 2, "Close match with \"Placement report\".",
                        List.of(new com.campusconnect.campus_ai.service.PlagiarismService.SubmissionMatch(
                                7L, "Placement report", "aarav@campus.edu", "SUBMITTED",
                                0.82, 0.10, 0.05, 0.98))));

        mockMvc.perform(post("/evaluation/plagiarism/scan-all-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Applicants send resumes everywhere yet get few replies.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semanticComparisons").value(2))
                // The raw cosine is exposed so a reader can see that meaning,
                // not words, is what drove a 0.82 out of 0.10 word coverage.
                .andExpect(jsonPath("$.matches[0].semanticScore").value(0.98));

        verify(plagiarismService).scanAgainstAllSubmissions(anyString(), org.mockito.ArgumentMatchers.eq(vector));
    }

    /**
     * The check has to keep working with the model server down — that was true
     * before it had any model dependency. A failed embed must reach the service
     * as a null vector, not as a 500.
     */
    @Test
    void whenOllamaIsDownTheCrossScanStillAnswers() throws Exception {
        when(embeddingService.embed(anyString()))
                .thenThrow(new IllegalStateException("Connection refused"));
        when(plagiarismService.scanAgainstAllSubmissions(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new com.campusconnect.campus_ai.service.PlagiarismService.CrossScanResult(
                        "NONE", 0.0, 2, 40, 0,
                        "No word overlap with the 2 other submissions compared. Semantic "
                                + "comparison was unavailable, so a paraphrase of one of them "
                                + "would not be visible here.",
                        List.of()));

        mockMvc.perform(post("/evaluation/plagiarism/scan-all-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Applicants send resumes everywhere yet get few replies.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("NONE"))
                // Zero is the machine-readable form of "we could not check", so
                // the UI can say that instead of implying a clean result.
                .andExpect(jsonPath("$.semanticComparisons").value(0));

        verify(plagiarismService).scanAgainstAllSubmissions(anyString(), org.mockito.ArgumentMatchers.isNull());
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
