package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DocumentIngestionService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * File upload -> clean -> chunk -> embed -> pgvector
 */
@RestController
@RequestMapping("/documents")
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentIngestionService.IngestionResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long uploaderId = null;
        uploaderId = DemoIdentity.parseId(header);
        return ingestionService.ingest(file, uploaderId);
    }
}
