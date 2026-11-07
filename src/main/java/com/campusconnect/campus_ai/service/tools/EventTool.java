package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.service.EventAttendanceService;
import com.campusconnect.campus_ai.service.EventService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Tool for event operations: list upcoming, join/RSVP, my events.
 * Triggers: "upcoming events", "join event", "rsvp", "my events", "create event"
 */
@Component
public class EventTool implements Tool {

    private final EventService eventService;
    private final EventAttendanceService eventAttendanceService;

    public EventTool(EventService eventService, EventAttendanceService eventAttendanceService) {
        this.eventService = eventService;
        this.eventAttendanceService = eventAttendanceService;
    }

    @Override
    public String name() {
        return "eventManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"upcoming event", "upcoming events", "join event", "rsvp", "my event", "my events", "create event", "new event"};
    }

    @Override
    public boolean requiresAuth() {
        // Some operations (list events) are public, but join/rsvp/my events need auth
        return true; // Default to requiring auth; we handle public list separately
    }

    @Override
    public String description() {
        return "Manage events: list upcoming events, RSVP/join events, view your RSVPs, create new events (pending admin approval).";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("create event") || q.contains("new event")) {
            params.put("action", "create");
            params.put("title", extractEventTitle(intent));
            params.put("type", extractEventType(q));
            params.put("date", extractDate(q));
            params.put("description", extractDescription(intent));
            params.put("location", extractLocation(intent));
        } else if (q.contains("join event") || q.contains("rsvp") || q.contains("attend event")) {
            params.put("action", "join");
            Long eventId = extractNumberAfter(intent, "join event |rsvp for event |attend event ");
            params.put("eventId", eventId);
        } else if (q.contains("my event") || q.contains("rsvp'd") || q.contains("i joined")) {
            params.put("action", "myEvents");
        } else {
            params.put("action", "list");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("list".equals(action)) {
            var upcoming = eventService.search(null, true);
            if (upcoming.isEmpty()) {
                return ToolResult.success("There are no upcoming events right now.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Upcoming events:\n");
            for (var e : upcoming) {
                sb.append("  - ").append(e.getTitle()).append(" (").append(e.getType())
                        .append(") on ").append(e.getEventDate())
                        .append(" at ").append(e.getLocation()).append("\n");
            }
            sb.append("\nRSVP: POST /events/{id}/join when signed in");
            return ToolResult.successWithCards(sb.toString(), Map.of(), eventCards(upcoming));
        }

        if ("join".equals(action)) {
            if (userId == null) {
                return ToolResult.error("Authentication required", "Please log in to RSVP.");
            }
            Long eventId = (Long) params.get("eventId");
            if (eventId == null) {
                return ToolResult.clarification(
                        "Which event? Reply with the event ID: \"join event 5\" or GET /events?upcoming=true");
            }
            var result = eventAttendanceService.join(eventId, userId);
            return ToolResult.success(
                    "You joined the event. Attendee count: " + result.get("attendeeCount"),
                    Map.of("eventId", eventId, "status", result.get("status")));
        }

        if ("myEvents".equals(action)) {
            if (userId == null) {
                return ToolResult.error("Authentication required", "Please log in to view your events.");
            }
            var myEvents = eventAttendanceService.getMyEvents(userId);
            if (myEvents.isEmpty()) {
                return ToolResult.success("You haven't RSVP'd to any events yet.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Your RSVPs:\n");
            for (var e : myEvents) {
                sb.append("  - ").append(e.title()).append(" (").append(e.type())
                        .append(") on ").append(e.eventDate())
                        .append(" at ").append(e.location()).append("\n");
            }
            return ToolResult.successWithCards(sb.toString(), Map.of(), rsvpCards(myEvents));
        }

        if ("create".equals(action)) {
            if (userId == null) {
                return ToolResult.error("Authentication required", "Please log in to create events.");
            }
            String title = (String) params.get("title");
            String type = (String) params.get("type");
            String dateStr = (String) params.get("date");
            String description = (String) params.get("description");
            String location = (String) params.get("location");

            if (title == null || type == null || dateStr == null) {
                return ToolResult.clarification(
                        "To create an event, I need: title, type (HACKATHON|WORKSHOP|SEMINAR|NETWORKING|PLACEMENT), and date (YYYY-MM-DD). " +
                        "Try: \"create event Hackathon 2026 workshop on 2026-11-15 at the main auditorium\"");
            }

            LocalDate eventDate;
            try {
                eventDate = LocalDate.parse(dateStr);
            } catch (Exception e) {
                return ToolResult.error("Invalid date", "Date must be YYYY-MM-DD format. Got: " + dateStr);
            }

            try {
                Event event = eventService.createEvent(userId, title, type, eventDate, description, location);
                return ToolResult.success(
                        "Event created: #" + event.getId() + " \"" + event.getTitle() + "\" (" + event.getType() + ") on " + event.getEventDate() +
                        ". Note: your event is pending admin approval and will appear once approved.",
                        Map.of("eventId", event.getId(), "title", event.getTitle(), "status", "PENDING_APPROVAL"));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot create event", e.getMessage());
            }
        }

        return ToolResult.error("Unknown event action", "Action: " + action);
    }

    private static final Set<String> ALLOWED_TYPES = Set.of("HACKATHON", "WORKSHOP", "SEMINAR", "NETWORKING", "PLACEMENT");

    /** Clickable cards for a list of Event entities (upcoming). */
    private static List<ToolResult.EntityCard> eventCards(List<Event> events) {
        List<ToolResult.EntityCard> cards = new ArrayList<>();
        for (var e : events) {
            cards.add(new ToolResult.EntityCard(
                    "event",
                    e.getId(),
                    e.getTitle() == null ? "Event" : e.getTitle(),
                    (e.getType() == null ? "" : e.getType())
                            + (e.getEventDate() == null ? "" : " · " + e.getEventDate()),
                    e.getLocation() == null ? "Tap to view" : e.getLocation(),
                    "/events/" + e.getId()));
        }
        return cards;
    }

    /** Clickable cards for a list of RSVP EventDto rows. */
    private static List<ToolResult.EntityCard> rsvpCards(
            List<com.campusconnect.campus_ai.service.EventAttendanceService.EventDto> events) {
        List<ToolResult.EntityCard> cards = new ArrayList<>();
        for (var e : events) {
            cards.add(new ToolResult.EntityCard(
                    "event",
                    e.eventId(),
                    e.title() == null ? "Event" : e.title(),
                    (e.type() == null ? "" : e.type())
                            + (e.eventDate() == null ? "" : " · " + e.eventDate()),
                    e.location() == null ? "Tap to view" : e.location(),
                    "/events/" + e.eventId()));
        }
        return cards;
    }

    private String extractEventTitle(String intent) {
        // "create event Hackathon 2026 workshop on 2026-11-15" -> "Hackathon 2026"
        // Extract text between "create event" / "new event" and date/type keywords
        String q = intent.toLowerCase(Locale.ROOT);
        int start = -1;
        if (q.contains("create event ")) start = q.indexOf("create event ") + "create event ".length();
        else if (q.contains("new event ")) start = q.indexOf("new event ") + "new event ".length();
        if (start < 0) return intent.trim();

        String rest = intent.substring(start).trim();
        // Cut at type keywords
        for (String typeWord : ALLOWED_TYPES) {
            String lowerType = typeWord.toLowerCase(Locale.ROOT);
            int idx = rest.toLowerCase(Locale.ROOT).indexOf(lowerType);
            if (idx >= 0) {
                rest = rest.substring(0, idx).trim();
                break;
            }
        }
        // Cut at date pattern
        String dateMatch = extractDate(rest);
        if (dateMatch != null) {
            int idx = rest.indexOf(dateMatch);
            if (idx >= 0) rest = rest.substring(0, idx).trim();
        }
        // Cut at "on " or "at " for description
        for (String sep : new String[]{" on ", " at ", " - "}) {
            int idx = rest.indexOf(sep);
            if (idx >= 0) {
                rest = rest.substring(0, idx).trim();
                break;
            }
        }
        // Remove trailing "workshop/seminar/etc" if title ended with it
        for (String typeWord : ALLOWED_TYPES) {
            String lowerType = typeWord.toLowerCase(Locale.ROOT);
            if (rest.toLowerCase(Locale.ROOT).endsWith(lowerType)) {
                rest = rest.substring(0, rest.length() - lowerType.length()).trim();
            }
        }
        return rest.isEmpty() ? null : rest;
    }

    private String extractEventType(String q) {
        for (String t : ALLOWED_TYPES) {
            if (q.contains(t.toLowerCase(Locale.ROOT))) return t;
        }
        return null;
    }

    private String extractDate(String text) {
        String q = text.toLowerCase(Locale.ROOT);
        // Match YYYY-MM-DD
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(q);
        if (m.find()) return m.group();
        // Match YYYY/MM/DD
        m = java.util.regex.Pattern.compile("\\d{4}/\\d{2}/\\d{2}").matcher(q);
        if (m.find()) return m.group().replace('/', '-');
        // Match "on DD/MM/YYYY" or "on DD-MM-YYYY"
        m = java.util.regex.Pattern.compile("on (\\d{2}[-/]\\d{2}[-/]\\d{4})").matcher(q);
        if (m.find()) {
            String d = m.group(1);
            if (d.contains("/")) {
                String[] parts = d.split("/");
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
            String[] parts = d.split("-");
            return parts[2] + "-" + parts[0] + "-" + parts[1];
        }
        return null;
    }

    private String extractDescription(String intent) {
        // "create event Title type on DATE at LOCATION with a description about X"
        String q = intent.toLowerCase(Locale.ROOT);
        int start = q.contains("create event") ? q.indexOf("create event") : q.indexOf("new event");
        if (start < 0) return null;

        String after = intent.substring(start).toLowerCase(Locale.ROOT);
        // Look for "about" or "description"
        for (String kw : new String[]{"about ", "description "}) {
            int idx = after.indexOf(kw);
            if (idx >= 0) return intent.substring(start + idx + kw.length()).trim();
        }
        return null;
    }

    private String extractLocation(String intent) {
        // "at the main auditorium" or "in room 101"
        String q = intent.toLowerCase(Locale.ROOT);
        for (String kw : new String[]{" at ", " in "}) {
            int idx = q.indexOf(kw);
            if (idx >= 0) {
                String loc = intent.substring(idx + kw.length()).trim();
                // Remove trailing date/description keywords
                loc = loc.replaceFirst("\\s+(on|at|about|description|with).*$", "").trim();
                if (!loc.isEmpty()) return loc;
            }
        }
        return null;
    }

    private Long extractNumberAfter(String text, String pattern) {
        String[] parts = pattern.split("\\|");
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pat : parts) {
            String p = pat.trim();
            int idx = lower.indexOf(p);
            if (idx >= 0) {
                String rest = text.substring(idx + p.length()).trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(rest);
                if (m.find()) {
                    try {
                        return Long.parseLong(m.group());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }
}