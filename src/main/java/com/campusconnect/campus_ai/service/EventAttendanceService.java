package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.entity.EventAttendance;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.EventAttendanceRepository;
import com.campusconnect.campus_ai.repository.EventRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EventAttendanceService {

    private final EventAttendanceRepository attendanceRepository;
    private final EventRepository eventRepository;
    private final StudentRepository studentRepository;

    public EventAttendanceService(EventAttendanceRepository attendanceRepository,
                                  EventRepository eventRepository,
                                  StudentRepository studentRepository) {
        this.attendanceRepository = attendanceRepository;
        this.eventRepository = eventRepository;
        this.studentRepository = studentRepository;
    }

    public Map<String, Object> join(Long eventId, Long studentId) {
        if (eventId == null || studentId == null) throw new IllegalArgumentException("eventId and studentId required");
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        if (event.isHidden()) throw new IllegalArgumentException("Event is pending approval and not open for RSVP yet");
        if (attendanceRepository.existsByEventIdAndStudentId(eventId, studentId)) {
            return Map.of("eventId", eventId, "studentId", studentId, "status", "already_joined", "attendeeCount", attendanceRepository.countByEventId(eventId));
        }
        attendanceRepository.save(new EventAttendance(event, student));
        return Map.of("eventId", eventId, "studentId", studentId, "status", "joined", "attendeeCount", attendanceRepository.countByEventId(eventId));
    }

    public Map<String, Object> leave(Long eventId, Long studentId) {
        var existing = attendanceRepository.findByEventIdAndStudentId(eventId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Not joined: event " + eventId + " student " + studentId));
        attendanceRepository.delete(existing);
        return Map.of("eventId", eventId, "studentId", studentId, "status", "left", "attendeeCount", attendanceRepository.countByEventId(eventId));
    }

    public List<AttendeeDto> getAttendees(Long eventId) {
        eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        return attendanceRepository.findByEventIdOrderByJoinedAtAsc(eventId).stream()
                .map(a -> new AttendeeDto(a.getId(), a.getStudent().getId(), a.getStudent().getName(), a.getStudent().getBranch(), a.getJoinedAt() != null ? a.getJoinedAt().toString() : null))
                .collect(Collectors.toList());
    }

    public List<EventDto> getMyEvents(Long studentId) {
        studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        return attendanceRepository.findByStudentIdOrderByJoinedAtDesc(studentId).stream()
                .map(a -> new EventDto(a.getEvent().getId(), a.getEvent().getTitle(), a.getEvent().getType(), a.getEvent().getEventDate() != null ? a.getEvent().getEventDate().toString() : null, a.getEvent().getLocation(), a.getJoinedAt() != null ? a.getJoinedAt().toString() : null))
                .collect(Collectors.toList());
    }

    public long count(Long eventId) {
        return attendanceRepository.countByEventId(eventId);
    }

    public record AttendeeDto(Long id, Long studentId, String studentName, String branch, String joinedAt) {}
    public record EventDto(Long eventId, String title, String type, String eventDate, String location, String joinedAt) {}
}
