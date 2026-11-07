package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.BookmarkService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

 /** POST /bookmarks/toggle {refType: POST|EVENT|PROJECT, refId} — save/remove */
    @PostMapping("/bookmarks/toggle")
    public Map<String, Object> toggle(
            @RequestBody Map<String, Object> body) {
        Long studentId = AuthContext.id();
        String refType = body != null && body.get("refType") instanceof String s ? s : (body != null && body.get("type") instanceof String t ? t : "");
        Long refId = body != null ? toLong(body.get("refId")) : null;
        if (refId == null) refId = body != null ? toLong(body.get("id")) : null;
        if (refId == null) throw new IllegalArgumentException("refId required");
        BookmarkService.BookmarkDto dto = bookmarkService.toggle(studentId, refType, refId);
        if (dto == null) return Map.of("status", "removed", "refType", refType.toUpperCase().trim(), "refId", refId);
        return Map.of("status", "saved", "bookmark", dto);
    }

 /** POST /bookmarks {refType, refId} — save (idempotent) */
    @PostMapping("/bookmarks")
    public Map<String, Object> save(
            @RequestBody Map<String, Object> body) {
        Long studentId = AuthContext.id();
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

 /** DELETE /bookmarks?refType=&refId= */
    @DeleteMapping("/bookmarks")
    public Map<String, Object> remove(
            @RequestParam String refType,
            @RequestParam Long refId) {
        Long studentId = AuthContext.id();
        return bookmarkService.remove(studentId, refType, refId);
    }

 /** GET /bookmarks?refType=POST|EVENT|PROJECT */
    @GetMapping("/bookmarks")
    public List<BookmarkService.BookmarkDto> list(
            @RequestParam(required = false) String refType) {
        Long studentId = AuthContext.id();
        return bookmarkService.list(studentId, refType);
    }

 /** GET /bookmarks/saved/{refType}/{refId} — is saved? */
    @GetMapping("/bookmarks/saved/{refType}/{refId}")
    public Map<String, Object> isSaved(
            @PathVariable String refType,
            @PathVariable Long refId) {
        Long studentId = AuthContext.id();
        boolean v = bookmarkService.isSaved(studentId, refType, refId);
        return Map.of("studentId", studentId, "refType", refType.toUpperCase().trim(), "refId", refId, "saved", v);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
