package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.service.MentorshipService;
import com.campusconnect.campus_ai.service.OllamaChatService;
import com.campusconnect.campus_ai.service.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MentorshipToolTest {

    private MentorshipService mentorshipService;
    private AlumniRepository alumniRepository;
    private OllamaChatService chatService;
    private MentorshipTool tool;

    @BeforeEach
    void setUp() {
        mentorshipService = mock(MentorshipService.class);
        alumniRepository = mock(AlumniRepository.class);
        chatService = mock(OllamaChatService.class);
        tool = new MentorshipTool(mentorshipService, alumniRepository, chatService);
    }

    private static Alumni makeAlumni(String name, Long id) {
        Alumni a = new Alumni();
        a.setName(name);
        try {
            Field idField = Alumni.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(a, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return a;
    }

    @Test
    void extractParams_withNumericId_extractsIdAndMessage() {
        var params = tool.extractParams("request mentor 5 with guidance on career growth");

        assertEquals("request", params.get("action"));
        assertEquals(5L, params.get("alumniId"));
        assertEquals("guidance on career growth", params.get("message"));
    }

    @Test
    void extractParams_withAlumniName_resolvesToId() {
        Alumni rahul = makeAlumni("Rahul", 42L);
        when(alumniRepository.findAll()).thenReturn(List.of(rahul));

        var params = tool.extractParams("request mentor from Rahul with career advice");

        assertEquals("request", params.get("action"));
        assertEquals(42L, params.get("alumniId"));
        assertEquals("career advice", params.get("message"));
    }

    @Test
    void extractParams_withUnresolvableName_returnsClarification() {
        when(alumniRepository.findAll()).thenReturn(List.of());

        var params = tool.extractParams("request mentor from NonExistent with some message");

        assertNull(params.get("alumniId"));
        assertNotNull(params.get("clarification"));
        assertTrue(((String) params.get("clarification")).contains("NonExistent"));
    }

    @Test
    void extractParams_withLLMFallback_extractsAlumniNameAndMessage() {
        // Rule-based extraction fails (no "from"/"with" for name, no numeric ID)
        // LLM should extract from "I want to connect with Sarah Johnson about internship opportunities"
        when(chatService.chat(anyString(), anyString())).thenReturn(
            "{\"alumniName\":\"Sarah\",\"message\":\"internship opportunities\"}"
        );

        Alumni sarah = makeAlumni("Sarah", 99L);
        when(alumniRepository.findAll()).thenReturn(List.of(sarah));

        var params = tool.extractParams("I want to connect with Sarah about internship opportunities");

        assertEquals("request", params.get("action"));
        assertEquals(99L, params.get("alumniId"));
        assertEquals("internship opportunities", params.get("message"));
    }

    @Test
    void extractParams_withLLMFallbackNullValues_returnsEmptyParams() {
        // LLM returns nulls for both fields
        when(chatService.chat(anyString(), anyString())).thenReturn(
            "{\"alumniName\":null,\"message\":null}"
        );

        var params = tool.extractParams("just want to find a mentor");

        assertEquals("request", params.get("action"));
        assertNull(params.get("alumniId"));
        assertNull(params.get("message"));
    }

    @Test
    void execute_withClarification_returnsClarificationResult() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "request");
        params.put("clarification", "I couldn't find an alumnus named 'Unknown'");

        ToolResult result = tool.execute(params, 1L);

        assertTrue(result.isClarification());
        assertEquals("I couldn't find an alumnus named 'Unknown'", result.text());
        verifyNoInteractions(mentorshipService);
    }

    @Test
    void execute_whenLLMFailsFallsBackGracefully() {
        // LLM throws an exception during extraction
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("Ollama down"));

        var params = tool.extractParams("I want to connect with Sarah about internship opportunities");

        assertEquals("request", params.get("action"));
        assertNull(params.get("alumniId"));
        // Should not throw exception, should gracefully fall through
    }

    @Test
    void extractParams_withAcceptAction_extractsRequestId() {
        var params = tool.extractParams("accept mentor request 3");

        assertEquals("respond", params.get("action"));
        assertEquals(true, params.get("accept"));
        assertEquals(3L, params.get("requestId"));
    }

    @Test
    void extractParams_withWithdrawAction_extractsRequestId() {
        var params = tool.extractParams("withdraw mentor request 7");

        assertEquals("withdraw", params.get("action"));
        assertEquals(7L, params.get("requestId"));
    }

    @Test
    void extractParams_withMyRequestsAction() {
        var params = tool.extractParams("my mentor requests");

        assertEquals("myRequests", params.get("action"));
        assertNull(params.get("alumniId"));
        assertNull(params.get("message"));
    }

    @Test
    void LLMFallbackNotCalledWhenRuleBasedExtractionSucceeds() {
        // When extractNumberAfter finds an ID, LLM should not be called
        var params = tool.extractParams("request mentor 5 with some message");

        assertEquals(5L, params.get("alumniId"));
        verifyNoInteractions(chatService);
    }

    @Test
    void LLMFallbackCalledWhenBothIdAndMessageExtractionFail() {
        when(chatService.chat(anyString(), anyString())).thenReturn(
            "{\"alumniName\":\"John\",\"message\":\"career advice\"}"
        );

        Alumni john = makeAlumni("John", 15L);
        when(alumniRepository.findAll()).thenReturn(List.of(john));

        // No numeric ID, no "with" keyword for message extraction
        var params = tool.extractParams("request mentor John about career advice");

        assertEquals(15L, params.get("alumniId"));
        assertEquals("career advice", params.get("message"));
        verify(chatService, atLeastOnce()).chat(anyString(), anyString());
    }
}
