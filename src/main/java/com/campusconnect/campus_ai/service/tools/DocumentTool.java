package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Document;
import com.campusconnect.campus_ai.service.DocumentService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for document workflows: list documents, upload, view my uploads, hide/unhide (admin).
 * Triggers: "document", "documents", "upload", "my documents", "my uploads"
 */
@Component
public class DocumentTool implements Tool {

    private final DocumentService documentService;

    public DocumentTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() {
        return "documentManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"document", "documents", "upload", "my document", "my documents", "my upload", "my uploads", "hide document", "unhide document"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Browse documents, view your uploads, upload new documents, or manage visibility (admin).";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("my document") || q.contains("my upload")) {
            params.put("action", "myUploads");
        } else if (q.contains("hide") && q.contains("document")) {
            params.put("action", "hide");
            Long docId = extractNumberAfter(intent, "hide |document ");
            params.put("docId", docId);
        } else if (q.contains("unhide") && q.contains("document")) {
            params.put("action", "unhide");
            Long docId = extractNumberAfter(intent, "unhide |document ");
            params.put("docId", docId);
        } else if (q.contains("stats") || q.contains("statistics")) {
            params.put("action", "stats");
        } else if (q.contains("upload") || q.contains("add document") || q.contains("create document") || q.contains("save document")) {
            params.put("action", "upload");
            params.put("title", extractTitle(intent));
            params.put("content", extractContent(intent));
        } else {
            params.put("action", "list");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("list".equals(action)) {
            var docs = documentService.getVisibleDocuments();
            if (docs.isEmpty()) {
                return ToolResult.success("No documents available.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Available documents:\n");
            int show = Math.min(10, docs.size());
            for (int i = 0; i < show; i++) {
                var d = docs.get(i);
                var dto = documentService.toDto(d);
                sb.append("  - #").append(dto.id()).append(" ").append(dto.title())
                        .append(" (").append(dto.contentLength()).append(" chars)");
                if (dto.uploaderId() != null) sb.append(" — uploaded by ").append(dto.uploaderId());
                sb.append("\n");
            }
            if (docs.size() > show) sb.append("  ... and ").append(docs.size() - show).append(" more\n");
            sb.append("Upload: POST /documents with title + content (multipart/form-data)");
            return ToolResult.success(sb.toString(), Map.of("count", docs.size()));
        }

        if ("myUploads".equals(action)) {
            var docs = documentService.getMyUploads(userId);
            if (docs.isEmpty()) {
                return ToolResult.success("You haven't uploaded any documents yet.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Your uploads:\n");
            for (var d : docs) {
                var dto = documentService.toDto(d);
                sb.append("  - #").append(dto.id()).append(" ").append(dto.title())
                        .append(" [").append(dto.hidden() ? "HIDDEN" : "VISIBLE").append("]")
                        .append(" (").append(dto.contentLength()).append(" chars)\n");
            }
            return ToolResult.success(sb.toString(), Map.of("count", docs.size()));
        }

        if ("upload".equals(action)) {
            if (userId == null) {
                return ToolResult.error("Authentication required", "Please log in to upload documents.");
            }
            String title = (String) params.get("title");
            String content = (String) params.get("content");

            if (title == null || title.equals("Untitled document")) {
                return ToolResult.clarification(
                        "What title for your document? Try: \"upload 'My Notes' with content I love AI\"");
            }

            if (content == null || content.isBlank()) {
                return ToolResult.clarification(
                        "What content to save under '" + title + "'? Try: \"upload 'Meeting Notes' with content discussed roadmap and timeline\"");
            }

            String cleanedTitle = sanitize(title, 200);
            String cleanedContent = sanitize(content, 10000);

            try {
                Document doc = new Document();
                doc.setTitle(cleanedTitle);
                doc.setContent(cleanedContent);
                doc.setUploaderId(userId);
                doc.setHidden(false);
                Document saved = documentService.saveDocument(doc);
                var dto = documentService.toDto(saved);
                return ToolResult.success(
                        "Document saved: #" + dto.id() + " \"" + dto.title() + "\" (" + dto.contentLength() + " chars)",
                        Map.of("docId", dto.id(), "title", dto.title(), "contentLength", dto.contentLength()));
            } catch (Exception e) {
                return ToolResult.error("Cannot upload document", e.getMessage());
            }
        }

        if ("hide".equals(action) || "unhide".equals(action)) {
            Long docId = (Long) params.get("docId");
            if (docId == null) {
                return ToolResult.clarification(
                        "Which document? Try: \"hide document 5\" or \"unhide document 5\"");
            }
            try {
                boolean hidden = "hide".equals(action);
                var d = documentService.hideDocument(docId, hidden);
                var dto = documentService.toDto(d);
                return ToolResult.success("Document " + (hidden ? "hidden" : "unhidden") + ": " + dto.title(),
                        Map.of("docId", dto.id(), "hidden", dto.hidden()));
            } catch (Exception e) {
                return ToolResult.error("Admin action failed", e.getMessage());
            }
        }

        if ("stats".equals(action)) {
            var stats = documentService.stats();
            StringBuilder sb = new StringBuilder("Document stats:\n");
            stats.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            return ToolResult.success(sb.toString(), stats);
        }

        return ToolResult.error("Unknown document action", "Action: " + action);
    }

    private String extractTitle(String intent) {
        // "upload 'My Notes' with content ..." or "create document about AI"
        String q = intent.toLowerCase(Locale.ROOT);
        // Look for quoted title
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"|'([^']+)'").matcher(intent);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        // Look for "about X"
        int aboutIdx = q.indexOf(" about ");
        if (aboutIdx >= 0) {
            String rest = intent.substring(aboutIdx + 7);
            // Cut at content keyword
            for (String sep : new String[]{" with content ", " with ", " containing "}) {
                int idx = rest.toLowerCase(Locale.ROOT).indexOf(sep);
                if (idx >= 0) return rest.substring(0, idx).trim();
            }
            return rest.trim();
        }
        return "Untitled document";
    }

    private String extractContent(String intent) {
        String q = intent.toLowerCase(Locale.ROOT);
        // Look for "with content X", "containing X", "with X"
        for (String kw : new String[]{"with content ", "containing ", "with "}) {
            int idx = q.indexOf(kw);
            if (idx >= 0) {
                String content = intent.substring(idx + kw.length()).trim();
                // If there's a quoted section, extract just that
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"|'([^']+)'").matcher(content);
                if (m.find()) {
                    return m.group(1) != null ? m.group(1) : m.group(2);
                }
                return content;
            }
        }
        return null;
    }

    private String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String cleaned = value.replaceAll("<[^>]+>", "").trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
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