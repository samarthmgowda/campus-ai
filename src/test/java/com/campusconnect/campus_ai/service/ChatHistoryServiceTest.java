package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.ChatTurn;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.ChatTurnRepository;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the conversation (thread) logic in {@link ChatHistoryService}.
 * The repository is mocked, so no database is required.
 */
class ChatHistoryServiceTest {

    private final ChatTurnRepository chatTurnRepository = mock(ChatTurnRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final AlumniRepository alumniRepository = mock(AlumniRepository.class);
    private final RecruiterRepository recruiterRepository = mock(RecruiterRepository.class);

    private ChatHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ChatHistoryService(chatTurnRepository, studentRepository, alumniRepository, recruiterRepository);
    }

    private Student student(long id, String name) {
        Student s = new Student(name, "CSE", 3, "Java", "AI", null, null, null, "AVAILABLE");
        try {
            var f = Student.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    private ChatTurn turn(String conversationId, String question, LocalDateTime at) {
        ChatTurn t = new ChatTurn();
        t.setConversationId(conversationId);
        t.setQuestion(question);
        t.setAnswer("answer to " + question);
        t.setAnswered(true);
        t.setCreatedAt(at);
        return t;
    }

    @Test
    void saveNormalizesBlankConversationIdToDefault() {
        Student s = student(1L, "Asha");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(s));

        service.save(1L, "q?", "a.", true, null, "   ");

        ArgumentCaptor<ChatTurn> captor = ArgumentCaptor.forClass(ChatTurn.class);
        verify(chatTurnRepository).save(captor.capture());
        assertEquals(ChatHistoryService.DEFAULT_CONVERSATION, captor.getValue().getConversationId());
    }

    @Test
    void savePreservesExplicitConversationId() {
        Student s = student(1L, "Asha");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(s));

        service.save(1L, "q?", "a.", true, null, "conv-123");

        ArgumentCaptor<ChatTurn> captor = ArgumentCaptor.forClass(ChatTurn.class);
        verify(chatTurnRepository).save(captor.capture());
        assertEquals("conv-123", captor.getValue().getConversationId());
        assertSame(s, captor.getValue().getStudent());
    }

    @Test
    void saveSkipsWhenStudentMissing() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        service.save(99L, "q?", "a.", true, null, "conv-1");
        verify(chatTurnRepository, never()).save(any());
    }

    @Test
    void getHistoryForConversationNormalizesBlankIdAndCapsLimit() {
        when(chatTurnRepository.findByStudentIdAndConversationIdOrderByCreatedAtDesc(
                eq(1L), eq(ChatHistoryService.DEFAULT_CONVERSATION)))
                .thenReturn(List.of(turn("", "q1", LocalDateTime.now())));

        List<ChatHistoryService.ChatTurnDto> out = service.getHistory(1L, "  ", 20);

        assertEquals(1, out.size());
        verify(chatTurnRepository).findByStudentIdAndConversationIdOrderByCreatedAtDesc(1L, "");
    }

    @Test
    void listConversationsUsesNewestQuestionAsTitleAndTruncates() {
        String longQ = "x".repeat(120);
        Object[] row = new Object[] { "conv-a", LocalDateTime.now() };
        when(chatTurnRepository.findConversations(1L)).thenReturn(List.<Object[]>of(row));
        when(chatTurnRepository.findByStudentIdAndConversationIdOrderByCreatedAtDesc(1L, "conv-a"))
                .thenReturn(List.of(
                        turn("conv-a", longQ, LocalDateTime.now()),
                        turn("conv-a", "older", LocalDateTime.now().minusMinutes(5))));

        List<ChatHistoryService.ConversationDto> out = service.listConversations(1L);

        assertEquals(1, out.size());
        assertEquals("conv-a", out.get(0).id());
        assertEquals(2, out.get(0).turnCount());
        assertTrue(out.get(0).title().endsWith("…"));
        assertEquals(61, out.get(0).title().length()); // 60 chars + ellipsis
    }

    @Test
    void listConversationsCoalescesNullConversationIdToDefault() {
        Object[] row = new Object[] { null, LocalDateTime.now() };
        when(chatTurnRepository.findConversations(1L)).thenReturn(List.<Object[]>of(row));
        when(chatTurnRepository.findByStudentIdAndConversationIdOrderByCreatedAtDesc(1L, ""))
                .thenReturn(List.of(turn("", "legacy q", LocalDateTime.now())));

        List<ChatHistoryService.ConversationDto> out = service.listConversations(1L);

        assertEquals(1, out.size());
        assertEquals(ChatHistoryService.DEFAULT_CONVERSATION, out.get(0).id());
    }

    @Test
    void clearConversationDeletesOnlyThatThreadAndReturnsCount() {
        when(chatTurnRepository.findByStudentIdAndConversationIdOrderByCreatedAtDesc(1L, "conv-b"))
                .thenReturn(List.of(turn("conv-b", "q1", LocalDateTime.now()), turn("conv-b", "q2", LocalDateTime.now())));

        long cleared = service.clearConversation(1L, "conv-b");

        assertEquals(2, cleared);
        verify(chatTurnRepository).deleteByStudentIdAndConversationId(1L, "conv-b");
        verify(chatTurnRepository, never()).deleteByStudentId(anyLong());
    }
}
