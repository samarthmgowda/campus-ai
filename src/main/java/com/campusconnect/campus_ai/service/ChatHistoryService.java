package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.ChatTurn;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ChatTurnRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    private final ChatTurnRepository chatTurnRepository;
    private final StudentRepository studentRepository;

    public ChatHistoryService(ChatTurnRepository chatTurnRepository, StudentRepository studentRepository) {
        this.chatTurnRepository = chatTurnRepository;
        this.studentRepository = studentRepository;
    }

    public void save(Long userId, String question, String answer, boolean answered) {
        if (userId == null || question == null) return;
        try {
            Student s = studentRepository.findById(userId).orElse(null);
            if (s == null) return;
            ChatTurn t = new ChatTurn();
            t.setStudent(s);
            t.setQuestion(question);
            t.setAnswer(answer != null ? answer : "");
            t.setAnswered(answered);
            t.setCreatedAt(LocalDateTime.now());
            chatTurnRepository.save(t);
        } catch (Exception ignored) {}
    }

    public List<ChatTurnDto> getHistory(Long userId, int limit) {
        int lim = Math.max(1, Math.min(limit, 50));
        List<ChatTurn> all = chatTurnRepository.findByStudentIdOrderByCreatedAtDesc(userId);
        return all.stream().limit(lim).map(this::toDto).collect(Collectors.toList());
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

    public record ChatTurnDto(Long id, String question, String answer, boolean answered, String createdAt) {}

    private ChatTurnDto toDto(ChatTurn t) {
        return new ChatTurnDto(t.getId(), t.getQuestion(), t.getAnswer(), t.isAnswered(),
                t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
    }
}
