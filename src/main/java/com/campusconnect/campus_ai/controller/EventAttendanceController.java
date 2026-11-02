package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.EventAttendanceService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class EventAttendanceController {

    private final EventAttendanceService attendanceService;

    public EventAttendanceController(EventAttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** POST /events/{eventId}/join with X-Demo-User */
    @PostMapping("/events/{eventId}/join")
    public Map<String, Object> join(@PathVariable Long eventId,
                                    @RequestHeader(value = "X-Demo-User", required = false) String header,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = resolve(header, body);
        return attendanceService.join(eventId, studentId);
    }

    /** POST /events/{eventId}/leave with X-Demo-User */
    @PostMapping("/events/{eventId}/leave")
    public Map<String, Object> leave(@PathVariable Long eventId,
                                     @RequestHeader(value = "X-Demo-User", required = false) String header,
                                     @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = resolve(header, body);
        return attendanceService.leave(eventId, studentId);
    }

    /** GET /events/{eventId}/attendees */
    @GetMapping("/events/{eventId}/attendees")
    public List<EventAttendanceService.AttendeeDto> attendees(@PathVariable Long eventId) {
        return attendanceService.getAttendees(eventId);
    }

    /** GET /events/{eventId}/attendee-count */
    @GetMapping("/events/{eventId}/attendee-count")
    public Map<String, Object> attendeeCount(@PathVariable Long eventId) {
        long c = attendanceService.count(eventId);
        return Map.of("eventId", eventId, "attendeeCount", c);
    }

    /** GET /events/my — my RSVPs with X-Demo-User */
    @GetMapping("/events/my")
    public List<EventAttendanceService.EventDto> myEvents(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long studentId = requireHeader(header);
        return attendanceService.getMyEvents(studentId);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        return DemoIdentity.requireId(header);
    }

    private Long resolve(String header, Map<String, Object> body) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        if (body != null) {
            Object v = body.get("studentId");
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException("X-Demo-User header or studentId required");
    }
}
