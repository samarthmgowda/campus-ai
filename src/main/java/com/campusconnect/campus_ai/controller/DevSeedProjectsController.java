package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.ProjectMember;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * TEMPORARY - remove after demo
 */
@RestController
@RequestMapping("/dev")
public class DevSeedProjectsController {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final StudentRepository studentRepository;

    public DevSeedProjectsController(ProjectRepository projectRepository,
                                     ProjectMemberRepository memberRepository,
                                     StudentRepository studentRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.studentRepository = studentRepository;
    }

    private record SeedProject(String name, String description, String domain, String requiredSkills,
                               String status, String ownerName, List<List<String>> members) {}

    @PostMapping("/seed-projects")
    public Map<String, Object> seedProjects() {
        List<SeedProject> data = List.of(
                new SeedProject("Campus AI", "AI intelligence layer for Campus Connect", "AI",
                        "Java, Python, React", "ACTIVE", "Aarav Sharma",
                        List.of(List.of("Aarav Sharma", "Backend"), List.of("Rohan Verma", "ML"))),
                new SeedProject("Smart Attendance IoT", "IoT-based attendance tracking", "IoT",
                        "C++, IoT, Mobile Development", "ACTIVE", "Vikram Patel",
                        List.of(List.of("Vikram Patel", "Hardware"))),
                new SeedProject("Campus Events Portal", "Portal for campus events and registrations", "Web",
                        "Java, React, Figma", "ACTIVE", "Aarav Sharma",
                        List.of(List.of("Aarav Sharma", "Backend")))
        );

        int projectsCreated = 0, membersCreated = 0;
        List<String> skipped = new ArrayList<>();

        Map<String, Student> byName = new HashMap<>();
        for (Student s : studentRepository.findAll()) byName.put(s.getName(), s);

        for (SeedProject sp : data) {
            if (projectRepository.findByName(sp.name()).isPresent()) {
                skipped.add(sp.name());
                continue;
            }
            Student owner = byName.get(sp.ownerName());
            if (owner == null) {
                skipped.add(sp.name() + " (missing owner: " + sp.ownerName() + ")");
                continue;
            }
            boolean missingMember = false;
            for (List<String> mm : sp.members()) {
                if (!byName.containsKey(mm.get(0))) {
                    skipped.add(sp.name() + " (missing member: " + mm.get(0) + ")");
                    missingMember = true;
                    break;
                }
            }
            if (missingMember) continue;

            Project p = new Project(sp.name(), sp.description(), sp.domain(), sp.requiredSkills(), sp.status(), owner);
            p = projectRepository.save(p);
            projectsCreated++;
            for (List<String> mm : sp.members()) {
                Student st = byName.get(mm.get(0));
                ProjectMember pm = new ProjectMember(p, st, mm.get(1));
                memberRepository.save(pm);
                membersCreated++;
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("projectsCreated", projectsCreated);
        resp.put("membersCreated", membersCreated);
        resp.put("skipped", skipped);
        return resp;
    }
}
