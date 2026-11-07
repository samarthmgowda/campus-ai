package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.EventAttendanceService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class EventAttendanceController {

    private final EventAttendanceService attendanceService;

    public EventAttendanceController(EventAttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

 /** POST /events/{eventId}/join */
    @PostMapping("/events/{eventId}/join")
    public Map<String, Object> join(@PathVariable Long eventId,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = AuthContext.id();
        return attendanceService.join(eventId, studentId);
    }

 /** POST /events/{eventId}/leave */
    @PostMapping("/events/{eventId}/leave")
    public Map<String, Object> leave(@PathVariable Long eventId,
                                     @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = AuthContext.id();
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

 /** GET /events/my — my RSVPs */
    @GetMapping("/events/my")
    public List<EventAttendanceService.EventDto> myEvents() {
        Long studentId = AuthContext.id();
        return attendanceService.getMyEvents(studentId);
    }

}
