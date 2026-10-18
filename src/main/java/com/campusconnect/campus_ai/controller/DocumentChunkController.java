package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.DocumentChunk;
import com.campusconnect.campus_ai.service.DocumentChunkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/documents")
public class DocumentChunkController {

    private final DocumentChunkService chunkService;

    public DocumentChunkController(DocumentChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @PostMapping("/{documentId}/chunks")
    public ResponseEntity<Map<String, Object>> addChunk(
            @PathVariable Long documentId,
            @RequestBody ChunkRequest request
    ) {
        DocumentChunk chunk = chunkService.addChunk(
                documentId,
                request.content(),
                request.pageNumber(),
                request.section()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("id", chunk.getId());
        response.put("documentId", documentId);
        response.put("content", chunk.getContent());
        response.put("pageNumber", chunk.getPageNumber());
        response.put("section", chunk.getSection());
        response.put("embeddingDimension", chunk.getEmbedding().length);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private record ChunkRequest(String content, Integer pageNumber, String section) {}
}
