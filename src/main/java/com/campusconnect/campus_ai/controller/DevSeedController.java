package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Document;
import com.campusconnect.campus_ai.repository.DocumentRepository;
import com.campusconnect.campus_ai.service.DocumentChunkService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * TEMPORARY - remove after demo data is loaded.
 */
@RestController
@RequestMapping("/dev")
public class DevSeedController {

    private final DocumentRepository documentRepository;
    private final DocumentChunkService chunkService;

    public DevSeedController(DocumentRepository documentRepository, DocumentChunkService chunkService) {
        this.documentRepository = documentRepository;
        this.chunkService = chunkService;
    }

    private record ChunkDef(String content, String section) {}

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        // Preserve insertion order for predictable response
        Map<String, List<ChunkDef>> data = new LinkedHashMap<>();
        data.put("SAMPLE - Hackathon Rules", List.of(
                new ChunkDef("Each hackathon team must contain between 2 and 4 students.", "Team Rules"),
                new ChunkDef("Each team must nominate one member as the team leader at registration.", "Team Rules"),
                new ChunkDef("Participants must submit their projects before the final deadline.", "Submission"),
                new ChunkDef("The judging panel will evaluate innovation and technical implementation.", "Judging"),
                new ChunkDef("Plagiarised or pre-built projects will lead to disqualification.", "Conduct")
        ));
        data.put("SAMPLE - Attendance Policy", List.of(
                new ChunkDef("Students must maintain at least 75 percent attendance in each subject to be eligible for semester exams.", "Eligibility"),
                new ChunkDef("Absence due to medical reasons requires a medical certificate submitted to the class coordinator within one week of returning.", "Medical Leave")
        ));
        data.put("SAMPLE - Placement Guidelines", List.of(
                new ChunkDef("Students with no active backlogs are eligible to attend campus placement drives.", "Eligibility"),
                new ChunkDef("Students must register for each placement drive on the placement portal before the registration deadline.", "Registration"),
                new ChunkDef("A student who accepts an offer from a company may be restricted from attending further drives, as decided by the placement cell.", "Offers")
        ));
        data.put("SAMPLE - Technical Fest Information", List.of(
                new ChunkDef("Registration for the technical fest closes one week before the event begins.", "Registration"),
                new ChunkDef("Workshops during the technical fest are open to all students and require prior registration.", "Workshops")
        ));

        int docsCreated = 0, chunksCreated = 0;
        List<String> skipped = new ArrayList<>();

        for (Map.Entry<String, List<ChunkDef>> entry : data.entrySet()) {
            String title = entry.getKey();
            if (documentRepository.findByTitle(title).isPresent()) {
                skipped.add(title);
                continue;
            }
            Document doc = new Document(title, title);
            doc = documentRepository.save(doc);
            docsCreated++;
            for (ChunkDef c : entry.getValue()) {
                chunkService.addChunk(doc.getId(), c.content(), 1, c.section());
                chunksCreated++;
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("documentsCreated", docsCreated);
        resp.put("chunksCreated", chunksCreated);
        resp.put("skipped", skipped);
        return resp;
    }
}
