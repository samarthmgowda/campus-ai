package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.repository.EventRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class EventService {

    private final EventRepository repository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;

    public EventService(EventRepository repository, StudentRepository studentRepository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
    }


    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("HACKATHON", "WORKSHOP", "SEMINAR", "NETWORKING", "PLACEMENT");

    public Event createEvent(Long userId, String title, String type, LocalDate eventDate, String description, String location) {
        currentUserService.requireUser(userId);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Event title required");
        String tt = title.trim();
        if (tt.length() < 3) throw new IllegalArgumentException("Event title must be at least 3 characters");
        if (tt.length() > 120) throw new IllegalArgumentException("Event title too long (max 120)");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Event type required (HACKATHON|WORKSHOP|SEMINAR|NETWORKING|PLACEMENT)");
        String tp = type.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(tp)) throw new IllegalArgumentException("Event type must be HACKATHON, WORKSHOP, SEMINAR, NETWORKING, or PLACEMENT");
        if (eventDate == null) throw new IllegalArgumentException("Event date required (YYYY-MM-DD)");
        // allow past dates for record-keeping, but warn via moderation; no hard block
        String desc = description != null ? description.trim() : null;
        if (desc != null && desc.length() > 2000) throw new IllegalArgumentException("Description too long (max 2000)");
        String loc = location != null ? location.trim() : null;
        if (loc != null && loc.length() > 200) throw new IllegalArgumentException("Location too long (max 200)");
        Event ev = new Event(tt, tp, eventDate, desc, loc);
        // New events start hidden (pending approval) — an admin approves via /events/admin/{id}/visibility.
        ev.setHidden(true);
        return repository.save(ev);
    }

    public List<Event> search(String type, Boolean upcoming) {
        boolean hasType = type != null && !type.isBlank();
        boolean wantUpcoming = Boolean.TRUE.equals(upcoming);
        LocalDate today = LocalDate.now();
        java.util.List<Event> raw;
        if (hasType && wantUpcoming) raw = repository.findByTypeIgnoreCaseAndEventDateGreaterThanEqual(type.trim(), today);
        else if (hasType) raw = repository.findByTypeIgnoreCase(type.trim());
        else if (wantUpcoming) raw = repository.findByEventDateGreaterThanEqual(today);
        else raw = repository.findAll();
        return raw.stream()
                .filter(e -> !e.isHidden())
                .sorted((a, b) -> {
                    LocalDate da = a.getEventDate();
                    LocalDate db = b.getEventDate();
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;  // null dates last
                    if (db == null) return -1;
                    return da.compareTo(db);
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /** A single event by id — for detail views and card deep links. */
    public Event getEvent(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + id));
    }

    /** Admin view: all events including pending (hidden). */
    public List<Event> getAllIncludingHidden() {
        return repository.findAll();
    }

    /** Admin approve (hidden=false) or hide (hidden=true). */
    public Event setVisibility(Long id, boolean hidden) {
        requireAdmin();
        Event ev = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
        ev.setHidden(hidden);
        return repository.save(ev);
    }

    /** Gate event approval on the JWT principal's ADMIN role. */
    public void requireAdmin() {
        if (!AuthContext.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Admin role required");
        }
    }
}
