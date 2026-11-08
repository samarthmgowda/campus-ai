package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.ChatTurn;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.ChatTurnRepository;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    /** Conversation id used for turns that predate threads or arrive without one. */
    public static final String DEFAULT_CONVERSATION = "";

    private final ChatTurnRepository chatTurnRepository;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final RecruiterRepository recruiterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatHistoryService(ChatTurnRepository chatTurnRepository,
                              StudentRepository studentRepository,
                              AlumniRepository alumniRepository,
                              RecruiterRepository recruiterRepository) {
        this.chatTurnRepository = chatTurnRepository;
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.recruiterRepository = recruiterRepository;
    }

    public void save(Long userId, String question, String answer, boolean answered) {
        save(userId, question, answer, answered, null, null);
    }

    public void save(Long userId, String question, String answer, boolean answered,
                     List<ToolResult.EntityCard> cards) {
        save(userId, question, answer, answered, cards, null);
    }

    /**
     * Persist a chat turn for the acting profile.
     *
     * The {@code chat_turn.student_id} FK predates role-aware identity, so — as
     * elsewhere in the demo — the row is keyed by the student table. Alumni and
     * recruiter logins are skipped rather than mis-keyed into the student table;
     * their turns simply do not appear in the history drawer.
     *
     * {@code conversationId} scopes the turn to a thread. A null or blank value
     * is normalised to {@link #DEFAULT_CONVERSATION} so legacy and new rows group
     * together rather than splitting into two "empty" threads.
     */
    public void save(Long userId, String question, String answer, boolean answered,
                     List<ToolResult.EntityCard> cards, String conversationId) {
        if (userId == null || question == null) return;
        try {
            Student s = studentRepository.findById(userId).orElse(null);
            if (s == null) return;
            ChatTurn t = new ChatTurn();
            t.setStudent(s);
            t.setConversationId(normalizeConversation(conversationId));
            t.setQuestion(question);
            t.setAnswer(answer != null ? answer : "");
            t.setAnswered(answered);
            t.setCards(serializeCards(cards));
            t.setCreatedAt(LocalDateTime.now());
            chatTurnRepository.save(t);
        } catch (Exception ignored) {}
    }

    private String normalizeConversation(String conversationId) {
        return (conversationId == null || conversationId.isBlank())
                ? DEFAULT_CONVERSATION : conversationId.trim();
    }

    /**
     * History for the default conversation (legacy behaviour). Kept so existing
     * callers that do not speak threads still get their most recent turns.
     */
    public List<ChatTurnDto> getHistory(Long userId, int limit) {
        int lim = Math.max(1, Math.min(limit, 50));
        List<ChatTurn> all = chatTurnRepository.findByStudentIdOrderByCreatedAtDesc(userId);
        return all.stream().limit(lim).map(this::toDto).collect(Collectors.toList());
    }

    /**
     * History for a single conversation, newest-first so it matches the shape
     * the transcript component already expects (it reverses before rendering).
     */
    public List<ChatTurnDto> getHistory(Long userId, String conversationId, int limit) {
        int lim = Math.max(1, Math.min(limit, 100));
        List<ChatTurn> turns = chatTurnRepository
                .findByStudentIdAndConversationIdOrderByCreatedAtDesc(userId, normalizeConversation(conversationId));
        return turns.stream().limit(lim).map(this::toDto).collect(Collectors.toList());
    }

    /** Distinct conversations for a student, newest first, with a preview of the first question. */
    public List<ConversationDto> listConversations(Long userId) {
        List<Object[]> rows = chatTurnRepository.findConversations(userId);
        List<ConversationDto> out = new ArrayList<>();
        for (Object[] row : rows) {
            String convId = row[0] != null ? row[0].toString() : DEFAULT_CONVERSATION;
            // Newest-first: the first element is the most recent turn.
            List<ChatTurn> turns = chatTurnRepository
                    .findByStudentIdAndConversationIdOrderByCreatedAtDesc(userId, convId);
            if (turns.isEmpty()) continue;
            String title = turns.get(0).getQuestion();
            if (title != null && title.length() > 60) title = title.substring(0, 60) + "…";
            String lastAt = turns.get(0).getCreatedAt() != null
                    ? turns.get(0).getCreatedAt().toString() : null;
            out.add(new ConversationDto(convId, title, turns.size(), lastAt));
        }
        return out;
    }

    public long count(Long userId) {
        return chatTurnRepository.countByStudentId(userId);
    }

    @Transactional
    public long clear(Long userId) {
        long c = chatTurnRepository.countByStudentId(userId);
        chatTurnRepository.deleteByStudentId(userId);
        return c;
    }

    @Transactional
    public long clearConversation(Long userId, String conversationId) {
        String conv = normalizeConversation(conversationId);
        long c = chatTurnRepository
                .findByStudentIdAndConversationIdOrderByCreatedAtDesc(userId, conv).size();
        chatTurnRepository.deleteByStudentIdAndConversationId(userId, conv);
        return c;
    }

    public record ChatTurnDto(Long id, String question, String answer, boolean answered,
                              String createdAt, List<ToolResult.EntityCard> cards) {}

    public record ConversationDto(String id, String title, int turnCount, String lastMessageAt) {}

    private ChatTurnDto toDto(ChatTurn t) {
        return new ChatTurnDto(t.getId(), t.getQuestion(), t.getAnswer(), t.isAnswered(),
                t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                deserializeCards(t.getCards()));
    }

    private String serializeCards(List<ToolResult.EntityCard> cards) {
        if (cards == null || cards.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(cards);
        } catch (Exception e) {
            return null;
        }
    }

    private List<ToolResult.EntityCard> deserializeCards(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ToolResult.EntityCard>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
