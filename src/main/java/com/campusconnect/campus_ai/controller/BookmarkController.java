package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.BookmarkService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    /** POST /bookmarks/toggle {refType: POST|EVENT|PROJECT, refId} with X-Demo-User — save/remove */
    @PostMapping("/bookmarks/toggle")
    public Map<String, Object> toggle(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long studentId = requireHeader(header, body);
        String refType = body != null && body.get("refType") instanceof String s ? s : (body != null && body.get("type") instanceof String t ? t : "");
        Long refId = body != null ? toLong(body.get("refId")) : null;
        if (refId == null) refId = body != null ? toLong(body.get("id")) : null;
        if (refId == null) throw new IllegalArgumentException("refId required");
        BookmarkService.BookmarkDto dto = bookmarkService.toggle(studentId, refType, refId);
        if (dto == null) return Map.of("status", "removed", "refType", refType.toUpperCase().trim(), "refId", refId);
        return Map.of("status", "saved", "bookmark", dto);
    }

    /** POST /bookmarks {refType, refId} with X-Demo-User — save (idempotent) */
    @PostMapping("/bookmarks")
    public Map<String, Object> save(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long studentId = requireHeader(header, body);
        String refType = body != null && body.get("refType") instanceof String s ? s : "";
        Long refId = body != null ? toLong(body.get("refId")) : null;
        if (refId == null) throw new IllegalArgumentException("refId required");
        // if already saved, keep saved
        if (bookmarkService.isSaved(studentId, refType, refId)) {
            return Map.of("status", "already_saved", "refType", refType.toUpperCase().trim(), "refId", refId);
        }
        BookmarkService.BookmarkDto dto = bookmarkService.toggle(studentId, refType, refId);
        return Map.of("status", "saved", "bookmark", dto);
    }

    /** DELETE /bookmarks?refType=&refId= with X-Demo-User */
    @DeleteMapping("/bookmarks")
    public Map<String, Object> remove(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam String refType,
            @RequestParam Long refId) {
        Long studentId = requireHeader(header, null);
        return bookmarkService.remove(studentId, refType, refId);
    }

    /** GET /bookmarks?refType=POST|EVENT|PROJECT with X-Demo-User */
    @GetMapping("/bookmarks")
    public List<BookmarkService.BookmarkDto> list(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) String refType) {
        Long studentId = requireHeader(header, null);
        return bookmarkService.list(studentId, refType);
    }

    /** GET /bookmarks/saved/{refType}/{refId} with X-Demo-User — is saved? */
    @GetMapping("/bookmarks/saved/{refType}/{refId}")
    public Map<String, Object> isSaved(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @PathVariable String refType,
            @PathVariable Long refId) {
        Long studentId = requireHeader(header, null);
        boolean v = bookmarkService.isSaved(studentId, refType, refId);
        return Map.of("studentId", studentId, "refType", refType.toUpperCase().trim(), "refId", refId, "saved", v);
    }

    private Long requireHeader(String header, Map<String, Object> body) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        if (body != null) {
            Object v = body.get("studentId");
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException("X-Demo-User header required");
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
