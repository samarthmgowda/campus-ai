package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Document;
import com.campusconnect.campus_ai.entity.DocumentChunk;
import com.campusconnect.campus_ai.repository.DocumentChunkRepository;
import com.campusconnect.campus_ai.repository.DocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_TYPES = Set.of("text/plain", "text/markdown", "text/x-markdown");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    // Structure-aware chunking helpers: page estimation + academic-year preservation (no new deps).
    private static final int CHARS_PER_PAGE = 3000;
    private static final Pattern PAGE_MARKER = Pattern.compile("(?i)(?:^|\\n)\\s*(?:---|\\*\\*\\*)?\\s*page\\s+(\\d+)(?:\\s+of\\s+\\d+)?\\s*(?:---|\\*\\*\\*)?\\s*(?:\\n|$)");
    private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\b((?:AY\\s*)?20\\d{2}\\s*[\\-–/]\\s*(?:20)?\\d{2})\\b");

    public DocumentIngestionService(DocumentRepository documentRepository,
                                     DocumentChunkRepository chunkRepository,
                                     EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    public record IngestionResult(Long documentId, String title, int chunksCreated, List<String> skippedSections) {}

    public IngestionResult ingest(MultipartFile file) { return ingest(file, null); }

    public IngestionResult ingest(MultipartFile file, Long uploaderId) {
        validate(file);
        String text = extractText(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("File contains no extractable text");
        }
        String title = sanitizeTitle(file.getOriginalFilename());

        Document doc = new Document(title, text);
        doc.setUploaderId(uploaderId);
        doc.setHidden(false);
        doc = documentRepository.save(doc);

        List<ParsedSection> sections = parseSections(text, title);
        // Document-level academic year fallback (e.g. handbook title or preamble carries the year).
        String docYear = findAcademicYear(text.length() > 2000 ? text.substring(0, 2000) : text);
        int created = 0;
        List<String> skipped = new ArrayList<>();

        for (int i = 0; i < sections.size(); i++) {
            ParsedSection section = sections.get(i);
            if (section.text().isBlank()) {
                skipped.add(section.headingPath());
                continue;
            }
            String year = section.academicYear() != null ? section.academicYear() : docYear;
            String sectionName = section.headingPath();
            if (year != null && !sectionName.contains(year)) {
                sectionName = sectionName + " (" + year + ")";
            }
            if (sectionName.length() > 200) sectionName = sectionName.substring(0, 200);
            List<ChunkPart> chunks = chunkSection(section);
            for (ChunkPart part : chunks) {
                // Embed with heading context so retrieval sees structure; store raw chunk text.
                String embedInput = "Document: " + title + "\nSection: " + sectionName + "\n" + part.text();
                float[] emb = embeddingService.embed(embedInput);
                DocumentChunk dc = new DocumentChunk(part.text(), part.pageNumber(), sectionName);
                dc.setDocument(doc);
                dc.setEmbedding(emb);
                chunkRepository.save(dc);
                created++;
            }
        }

        return new IngestionResult(doc.getId(), title, created, skipped);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds 5 MB limit");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only .txt and .md files are supported");
        }
    }

    private String extractText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file: " + e.getMessage(), e);
        }
    }

    private String sanitizeTitle(String filename) {
        if (filename == null || filename.isBlank()) return "Untitled";
        String name = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        if (name.length() > 200) name = name.substring(0, 200);
        return name.isBlank() ? "Untitled" : name;
    }

    /**
     * Structure-aware sectioning: markdown headings become titled sections with a
     * breadcrumb path (e.g. "Hackathon Rules > Team Formation"), each carrying its
     * offset-derived page estimate and nearest academic-year marker.
     * Head-of-document text before the first heading keeps the document title as path.
     */
    private record ParsedSection(String headingPath, String text, int startOffset, String academicYear) {}
    private record ChunkPart(String text, int pageNumber) {}

    private List<ParsedSection> parseSections(String text, String docTitle) {
        record Open(String path, StringBuilder body, int startOffset) {}
        List<Open> open = new ArrayList<>();
        List<String> stack = new ArrayList<>();

        List<ParsedSection> out = new ArrayList<>();
        // Offset-ordered academic-year markers: section takes nearest marker at-or-before its start.
        List<int[]> yearMarks = new ArrayList<>(); // [offset, markerIndex]
        List<String> yearVals = new ArrayList<>();
        var ym = ACADEMIC_YEAR.matcher(text);
        while (ym.find()) { yearMarks.add(new int[]{ym.start()}); yearVals.add(ym.group(1).trim()); }
        java.util.function.IntFunction<String> yearAt = (off) -> {
            String v = null;
            for (int i = 0; i < yearMarks.size(); i++) {
                if (yearMarks.get(i)[0] <= off) v = yearVals.get(i);
                else break;
            }
            return v;
        };
        StringBuilder preamble = new StringBuilder();
        int offset = 0;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            var hm = MARKDOWN_HEADING.matcher(line.trim());
            if (hm.matches()) {
                // Flush preamble once before the first heading.
                if (open.isEmpty() && !preamble.toString().isBlank()) {
                    out.add(new ParsedSection(docTitle, preamble.toString().trim(), 0, yearAt.apply(0)));
                    preamble = new StringBuilder();
                }
                int level = hm.group(1).length();
                String heading = hm.group(2).trim();
                while (stack.size() >= level) stack.remove(stack.size() - 1);
                stack.add(heading);
                open.add(new Open(String.join(" > ", stack), new StringBuilder(), offset + 1));
            } else if (open.isEmpty()) {
                preamble.append(line).append("\n");
            } else {
                Open cur = open.get(open.size() - 1);
                cur.body().append(line).append("\n");
            }
            offset += line.length() + 1;
        }
        if (!preamble.toString().isBlank()) {
            out.add(new ParsedSection(docTitle, preamble.toString().trim(), 0, yearAt.apply(0)));
        }
        for (Open o : open) {
            String body = o.body().toString().trim();
            if (body.isBlank()) continue;
            // Academic year = nearest marker at-or-before section start (heading included).
            out.add(new ParsedSection(o.path(), body, o.startOffset(), yearAt.apply(o.startOffset())));
        }
        if (out.isEmpty()) {
            out.add(new ParsedSection(docTitle, text.trim(), 0, yearAt.apply(0)));
        }
        return out;
    }

    private static String findAcademicYear(String snippet) {
        if (snippet == null) return null;
        var m = ACADEMIC_YEAR.matcher(snippet);
        String last = null;
        while (m.find()) last = m.group(1).trim();
        return last;
    }

    /**
     * Chunk one structured section: paragraph packing (~2800 chars), sentence-boundary
     * hard splits with 200-char overlap, page = marker (explicit "Page N") or
     * 1 + globalOffset / CHARS_PER_PAGE. Heading path travels as section metadata
     * (not inside chunk text) so grep/citations stay clean.
     */
    private List<ChunkPart> chunkSection(ParsedSection section) {
        String text = section.text();
        List<String> raw = packParagraphs(text);
        List<ChunkPart> out = new ArrayList<>();
        int consumed = 0;
        for (String c : raw) {
            int localStart = text.indexOf(c, consumed);
            if (localStart < 0) localStart = consumed;
            int page = pageForOffset(section, localStart);
            out.add(new ChunkPart(c, page));
            consumed = localStart + c.length();
        }
        return out.isEmpty() ? List.of(new ChunkPart(text.trim(), pageForOffset(section, 0))) : out;
    }

    private int pageForOffset(ParsedSection section, int localStart) {
        // Explicit "Page N" markers inside the section win.
        var m = PAGE_MARKER.matcher(section.text());
        int lastPage = -1;
        while (m.find()) {
            if (m.start() <= localStart) {
                try { lastPage = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
            }
        }
        if (lastPage > 0) return lastPage;
        int global = section.startOffset() + localStart;
        return Math.max(1, 1 + global / CHARS_PER_PAGE);
    }

    /**
     * Split text into overlapping chunks of ~300-700 tokens (~1200-2800 chars).
     * Uses paragraph boundaries first, then hard character limit with overlap.
     */
    private List<String> packParagraphs(String text) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\\s*\n");
        StringBuilder buffer = new StringBuilder();

        for (String p : paragraphs) {
            p = p.trim();
            if (p.isEmpty()) continue;

            if (buffer.length() + p.length() > 2800) {
                if (!buffer.isEmpty()) {
                    chunks.add(buffer.toString().trim());
                    buffer = new StringBuilder();
                }
            }
            if (buffer.length() > 0) buffer.append("\n\n");
            buffer.append(p);
        }
        if (!buffer.isEmpty()) {
            chunks.add(buffer.toString().trim());
        }

        // If still too large, hard-split with overlap
        List<String> finalChunks = new ArrayList<>();
        for (String c : chunks) {
            if (c.length() <= 2800) {
                finalChunks.add(c);
            } else {
                int start = 0;
                while (start < c.length()) {
                    int end = Math.min(start + 2800, c.length());
                    // Try to break at sentence boundary
                    int lastPeriod = c.lastIndexOf('.', end);
                    if (lastPeriod > start + 1200) end = lastPeriod + 1;
                    finalChunks.add(c.substring(start, end).trim());
                    start = end - 200; // 200-char overlap
                    if (start < 0) start = 0;
                }
            }
        }
        return finalChunks.isEmpty() ? List.of(text.trim()) : finalChunks;
    }
}