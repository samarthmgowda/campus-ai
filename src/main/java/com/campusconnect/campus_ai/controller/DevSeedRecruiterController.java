package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Recruiter;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * TEMPORARY - remove after demo
 */
@RestController
@RequestMapping("/dev")
public class DevSeedRecruiterController {

    private final RecruiterRepository recruiterRepository;

    public DevSeedRecruiterController(RecruiterRepository recruiterRepository) {
        this.recruiterRepository = recruiterRepository;
    }

    @PostMapping("/seed-recruiter")
    public Map<String, Object> seedRecruiter() {
        List<Map<String, String>> data = List.of(
                Map.of("name", "Rahul Mehta", "company", "Infosys", "role", "Tech Recruiter",
                        "email", "rahul.mehta@infosys.example.com", "linkedinUrl", "https://linkedin.com/in/rahulmehta",
                        "bio", "Recruiting for backend and cloud roles at Infosys."),
                Map.of("name", "Priya Desai", "company", "Flipkart", "role", "Hiring Manager",
                        "email", "priya.desai@flipkart.example.com", "linkedinUrl", "https://linkedin.com/in/priyadesai",
                        "bio", "Frontend and product engineering hiring at Flipkart.")
        );

        int created = 0;
        List<String> skipped = new ArrayList<>();

        for (Map<String, String> m : data) {
            String email = m.get("email");
            if (recruiterRepository.findByEmail(email).isPresent()) {
                skipped.add(email);
                continue;
            }
            Recruiter r = new Recruiter(m.get("name"), m.get("company"), m.get("role"),
                    email, m.get("linkedinUrl"), m.get("bio"));
            recruiterRepository.save(r);
            created++;
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("created", created);
        resp.put("skipped", skipped);
        return resp;
    }
}