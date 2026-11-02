package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.ProjectInviteService;
import com.campusconnect.campus_ai.service.OllamaChatService;
import com.campusconnect.campus_ai.service.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectInviteToolTest {

    private ProjectInviteService projectInviteService;
    private StudentRepository studentRepository;
    private OllamaChatService chatService;
    private ProjectInviteTool tool;

    @BeforeEach
    void setUp() {
        projectInviteService = mock(ProjectInviteService.class);
        studentRepository = mock(StudentRepository.class);
        chatService = mock(OllamaChatService.class);
        tool = new ProjectInviteTool(projectInviteService, studentRepository, chatService);
    }

    private static Student makeStudent(String name, Long id) {
        Student s = new Student();
        s.setName(name);
        try {
            Field idField = Student.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        s.setSuspended(false);
        return s;
    }

    // === Test Group 1: Numeric ID extraction ===

    @Test
    void extractParams_withNumericProjectAndId_extractsIds() {
        // numeric project ID and numeric invitee ID
        when(studentRepository.findAll()).thenReturn(Collections.emptyList());

        var params = tool.extractParams("invite project 3 user 5");

        assertEquals("invite", params.get("action"));
        assertEquals(3L, params.get("project"));
        assertEquals(5L, params.get("inviteeId"));
    }

    @Test
    void extractParams_withUnresolvableName_returnsClarification() {
        when(studentRepository.findAll()).thenReturn(Collections.emptyList());

        var params = tool.extractParams("invite Alex to project 3");

        assertEquals("invite", params.get("action"));
        assertNull(params.get("inviteeId"));
        assertNotNull(params.get("clarification"));
        assertTrue(((String) params.get("clarification")).contains("Alex"));
    }

    @Test
    void extractParams_withMyInvites_setsActionOnly() {
        var params = tool.extractParams("my invite");

        assertEquals("myInvites", params.get("action"));
        // extractParams only classifies intent; it must not hit the service.
        verifyNoInteractions(projectInviteService);
    }

    // === Test Group 2: LLM Fallback ===

    @Test
    void extractParams_withLLMFallback_extractsProjectAndName() {
        // Rule-based extraction fails (no "project 3" pattern, no "user 5" numeric)
        // LLM should extract from "invite Alex to my startup project"
        when(chatService.chat(anyString(), anyString())).thenReturn(
            "{\"projectName\":\"Startup\",\"inviteeName\":\"Alex\"}"
        );
        Student alex = makeStudent("Alex", 42L);
        when(studentRepository.findAll()).thenReturn(List.of(alex));

        var params = tool.extractParams("invite Alex to my startup project");

        assertEquals("invite", params.get("action"));
        // LLM should have resolved Alex -> 42
        assertEquals(42L, params.get("inviteeId"));
    }

    @Test
    void extractParams_withLLMFallbackNullValues_returnsEmptyParams() {
        // LLM returns nulls for both fields
        when(chatService.chat(anyString(), anyString())).thenReturn(
            "{\"projectName\":null,\"inviteeName\":null}"
        );

        var params = tool.extractParams("just want to invite someone");

        assertEquals("invite", params.get("action"));
        assertNull(params.get("project"));
        assertNull(params.get("inviteeId"));
    }

    @Test
    void extractParams_withLLMFallbackNotCalledWhenRulesSucceed() {
        // When extractNumberAfter finds IDs, LLM should not be called
        var params = tool.extractParams("invite project 3 user 5");

        assertEquals(3L, params.get("project"));
        assertEquals(5L, params.get("inviteeId"));
        verifyNoInteractions(chatService);
    }

    @Test
    void extractParams_withLLMCalledWhenBothIdPathsFail() {
        when(chatService.chat(anyString(), anyString())).thenReturn(
            "{\"projectName\":\"Hackathon\",\"inviteeName\":\"Sarah\"}"
        );
        Student sarah = makeStudent("Sarah", 15L);
        when(studentRepository.findAll()).thenReturn(List.of(sarah));

        // No numeric project ID, no "from"/"to" patterns for name extraction
        var params = tool.extractParams("invite Sarah to hackathon");

        assertEquals("invite", params.get("action"));
        assertEquals(15L, params.get("inviteeId"));
        verify(chatService, atLeastOnce()).chat(anyString(), anyString());
    }

    // === Test Group 3: Execution flow ===

    @Test
    void execute_withInviteAction_resolvesAllParams() {
        when(studentRepository.findAll()).thenReturn(List.of(makeStudent("Sarah", 15L)));
        when(projectInviteService.invite(anyLong(), anyLong(), anyLong())).thenReturn(
            new ProjectInviteService.InviteDto(1L, 1L, "Test Project", 1L, "Tester", 15L, "Sarah", "PENDING", "2026-01-01")
        );

        var params = tool.extractParams("invite Sarah to project 3");

        ToolResult result = tool.execute(params, 1L);
        assertTrue(result.success());
        assertEquals("Invitation sent to Sarah for Test Project (invite #1)", result.text());
    }

    @Test
    void execute_withUnresolvableName_returnsClarification() {
        when(studentRepository.findAll()).thenReturn(Collections.emptyList());

        var params = tool.extractParams("invite Alex to project 3");

        ToolResult result = tool.execute(params, 1L);
        assertTrue(result.isClarification());
        assertTrue(result.text().contains("Alex"));
    }

    @Test
    void execute_withAcceptInvite_succeeds() {
        when(projectInviteService.respond(anyLong(), anyLong(), anyString())).thenReturn(
            new ProjectInviteService.InviteDto(1L, 1L, "Test Project", 1L, "Tester", 2L, "Alex", "ACCEPTED", "2026-01-01")
        );

        var params = tool.extractParams("accept invite 1");

        ToolResult result = tool.execute(params, 1L);
        assertTrue(result.success());
        assertEquals("Invite accepted for Test Project", result.text());
    }

    @Test
    void execute_withRejectInvite_succeeds() {
        when(projectInviteService.respond(anyLong(), anyLong(), anyString())).thenReturn(
            new ProjectInviteService.InviteDto(1L, 1L, "Test Project", 1L, "Tester", 2L, "Alex", "REJECTED", "2026-01-01")
        );

        var params = tool.extractParams("reject invite 1");

        ToolResult result = tool.execute(params, 1L);
        assertTrue(result.success());
        assertEquals("Invite rejected for Test Project", result.text());
    }

    @Test
    void execute_withMyInvites_showsInvites() {
        // The tool queries with a null status filter, so the stub must match null.
        when(projectInviteService.getMyInvites(anyLong(), nullable(String.class))).thenReturn(
            List.of(new ProjectInviteService.InviteDto(
                1L, 1L, "Test Project", 1L, "Tester", 15L, "Sarah", "PENDING", "2026-01-01"))
        );

        var params = tool.extractParams("my invite");

        ToolResult result = tool.execute(params, 1L);
        assertTrue(result.success());
        assertEquals("Your project invitations:\n  - #1 Test Project from Tester [PENDING]\n", result.text());
    }

    @Test
    void execute_withNoInvites_returnsEmptyMessage() {
        when(projectInviteService.getMyInvites(anyLong(), nullable(String.class))).thenReturn(Collections.emptyList());

        var params = tool.extractParams("my invite");

        ToolResult result = tool.execute(params, 1L);
        assertTrue(result.success());
        assertEquals("You have no project invitations.", result.text());
    }
}