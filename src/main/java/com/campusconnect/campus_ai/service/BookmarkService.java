package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Bookmark;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookmarkService {

    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("POST", "EVENT", "PROJECT");

    private final BookmarkRepository bookmarkRepository;
    private final StudentRepository studentRepository;
    private final CommunityPostRepository postRepository;
    private final EventRepository eventRepository;
    private final ProjectRepository projectRepository;

    public BookmarkService(BookmarkRepository bookmarkRepository,
                           StudentRepository studentRepository,
                           CommunityPostRepository postRepository,
                           EventRepository eventRepository,
                           ProjectRepository projectRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.studentRepository = studentRepository;
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.projectRepository = projectRepository;
    }

    public BookmarkDto toggle(Long studentId, String refType, Long refId) {
        if (studentId == null) throw new IllegalArgumentException("studentId required");
        String t = normalizeType(refType);
        if (refId == null) throw new IllegalArgumentException("refId required");
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        validateExists(t, refId);
        var existing = bookmarkRepository.findByStudentIdAndRefTypeAndRefId(studentId, t, refId);
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return null; // signals removed
        }
        Bookmark b = new Bookmark(student, t, refId);
        b = bookmarkRepository.save(b);
        return toDto(b);
    }

    public Map<String, Object> save(Long studentId, String refType, Long refId) {
        BookmarkDto dto = toggle(studentId, refType, refId);
        if (dto == null) return Map.of("status", "removed", "refType", normalizeType(refType), "refId", refId);
        return Map.of("status", "saved", "bookmark", dto);
    }

    public Map<String, Object> remove(Long studentId, String refType, Long refId) {
        String t = normalizeType(refType);
        var existing = bookmarkRepository.findByStudentIdAndRefTypeAndRefId(studentId, t, refId)
                .orElseThrow(() -> new IllegalArgumentException("Bookmark not found for " + t + " " + refId));
        bookmarkRepository.delete(existing);
        return Map.of("status", "removed", "refType", t, "refId", refId);
    }

    public List<BookmarkDto> list(Long studentId, String refType) {
        studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        List<Bookmark> list;
        if (refType != null && !refType.isBlank()) {
            list = bookmarkRepository.findByStudentIdAndRefTypeOrderByCreatedAtDesc(studentId, normalizeType(refType));
        } else {
            list = bookmarkRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public boolean isSaved(Long studentId, String refType, Long refId) {
        return bookmarkRepository.existsByStudentIdAndRefTypeAndRefId(studentId, normalizeType(refType), refId);
    }

    public Map<String, Object> saveSummary(Long studentId) {
        var all = bookmarkRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        Map<String, Long> byType = all.stream().collect(Collectors.groupingBy(Bookmark::getRefType, Collectors.counting()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", studentId);
        m.put("total", all.size());
        m.put("byType", byType);
        return m;
    }

    public record BookmarkDto(Long id, String refType, Long refId, String createdAt) {}

    private String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("refType required (POST|EVENT|PROJECT)");
        String t = raw.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(t)) throw new IllegalArgumentException("refType must be POST, EVENT, or PROJECT");
        return t;
    }

    private void validateExists(String refType, Long refId) {
        switch (refType) {
            case "POST" -> postRepository.findById(refId).orElseThrow(() -> new IllegalArgumentException("Post not found: " + refId));
            case "EVENT" -> eventRepository.findById(refId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + refId));
            case "PROJECT" -> projectRepository.findById(refId).orElseThrow(() -> new IllegalArgumentException("Project not found: " + refId));
            default -> throw new IllegalArgumentException("Unknown refType: " + refType);
        }
    }

    private BookmarkDto toDto(Bookmark b) {
        return new BookmarkDto(b.getId(), b.getRefType(), b.getRefId(), b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
    }
}
