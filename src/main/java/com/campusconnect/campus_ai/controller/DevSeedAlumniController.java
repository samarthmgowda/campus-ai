package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Roadmap;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RoadmapRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * TEMPORARY - remove after demo
 */
@RestController
@RequestMapping("/dev")
public class DevSeedAlumniController {

    private final AlumniRepository alumniRepository;
    private final RoadmapRepository roadmapRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DevSeedAlumniController(AlumniRepository alumniRepository,
                                    RoadmapRepository roadmapRepository) {
        this.alumniRepository = alumniRepository;
        this.roadmapRepository = roadmapRepository;
    }

    private record SeedAlumni(String name, String branch, Integer graduationYear, String skills,
                              String currentRole, String currentCompany, String bio,
                              String linkedinUrl, String availability, List<SeedRoadmap> roadmaps) {}

    private record SeedRoadmap(String title, String targetRole, String description,
                               List<Map<String, String>> resources) {}

    private static Map<String, String> res(String type, String url, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("url", url);
        m.put("label", label);
        return m;
    }

    @PostMapping("/seed-alumni")
    public Map<String, Object> seedAlumni() throws Exception {
        List<SeedAlumni> data = List.of(
                new SeedAlumni("Rahul Sharma", "CSE", 2022,
                        "Java, Spring Boot, Microservices, System Design, AWS",
                        "Senior Backend Engineer", "Google",
                        "Built scalable payment systems at Google Pay.", "https://linkedin.com/in/rahulsharma",
                        "MENTORING",
                        List.of(
                                new SeedRoadmap("Backend Developer Roadmap", "Backend Engineer",
                                        "Start with Java basics, then Spring Boot, then databases (PostgreSQL), then microservices patterns, then cloud (AWS/GCP), then system design. Practice LeetCode medium for interviews.",
                                        List.of(
                                                res("website", "https://roadmap.sh/backend", "Backend roadmap on roadmap.sh"),
                                                res("youtube", "https://www.youtube.com/watch?v=9SGDpanrc8U", "Spring Boot full course"),
                                                res("blog", "https://spring.io/guides/gs/spring-boot", "Build a REST service with Spring Boot"),
                                                res("pdf", "https://martinfowler.com/articles/microservices.html", "Microservices primer"))),
                                new SeedRoadmap("Google Interview Prep", "Software Engineer",
                                        "Focus on data structures, algorithms, system design, and Google-specific culture questions. 3 months prep recommended.",
                                        List.of(
                                                res("website", "https://neetcode.io/roadmap", "NeetCode practice plan"),
                                                res("youtube", "https://www.youtube.com/@ByteByteGo", "System design walkthroughs"),
                                                res("blog", "https://www.google.com/about/careers/applications/how-we-hire/", "How Google hires")))
                        )),
                new SeedAlumni("Priya Patel", "ISE", 2021,
                        "Python, Machine Learning, TensorFlow, MLOps, Data Engineering",
                        "ML Engineer", "Microsoft",
                        "Work on recommendation systems and MLOps pipelines.", "https://linkedin.com/in/priyapatel",
                        "BOTH",
                        List.of(
                                new SeedRoadmap("ML Engineer Roadmap", "ML Engineer",
                                        "Python -> NumPy/Pandas -> ML theory (regression, classification, clustering) -> Deep Learning (PyTorch/TensorFlow) -> MLOps (MLflow, Kubeflow) -> Production ML systems. Kaggle competitions help.",
                                        List.of(
                                                res("website", "https://www.kaggle.com/learn", "Kaggle micro-courses"),
                                                res("youtube", "https://www.youtube.com/watch?v=aircAruvnKk", "Neural networks, visually explained"),
                                                res("blog", "https://madewithml.com/", "Made With ML — production ML"),
                                                res("pdf", "https://mml-book.github.io/", "Mathematics for Machine Learning (free book)")))
                        )),
                new SeedAlumni("Vikram Singh", "ECE", 2020,
                        "C++, Embedded Systems, IoT, RTOS, Hardware Design",
                        "Principal Firmware Engineer", "Tesla",
                        "Automotive firmware and IoT device architecture.", "https://linkedin.com/in/vikramsingh",
                        "REFERRALS",
                        List.of(
                                new SeedRoadmap("Embedded Engineer Roadmap", "Embedded Engineer",
                                        "C/C++ -> Microcontrollers (STM32, ESP32) -> RTOS (FreeRTOS) -> Communication protocols (I2C, SPI, CAN) -> Hardware bring-up -> Automotive standards (ISO 26262).",
                                        List.of(
                                                res("website", "https://www.freertos.org/Documentation/RTOS_book.html", "FreeRTOS reference manual"),
                                                res("youtube", "https://www.youtube.com/@FastbitEmbeddedBrainAcademy", "Embedded systems lectures"),
                                                res("blog", "https://interrupt.memfault.com/blog", "Memfault — embedded engineering blog")))
                        ))
        );

        int alumniCreated = 0, roadmapsCreated = 0;
        List<String> skipped = new ArrayList<>();

        Map<String, Alumni> byName = new HashMap<>();
        for (Alumni a : alumniRepository.findAll()) byName.put(a.getName(), a);

        for (SeedAlumni sa : data) {
            if (byName.containsKey(sa.name())) {
                skipped.add(sa.name());
                continue;
            }
            Alumni a = new Alumni(sa.name(), sa.branch(), sa.graduationYear(), sa.skills(),
                    sa.currentRole(), sa.currentCompany(), sa.bio(), sa.linkedinUrl(), sa.availability());
            a = alumniRepository.save(a);
            alumniCreated++;
            if (sa.roadmaps() != null) {
                for (SeedRoadmap sr : sa.roadmaps()) {
                    Roadmap r = new Roadmap(sr.title(), sr.targetRole(), sr.description(), a);
                    // Stored in the same JSON shape RoadmapController validates,
                    // so seeded and user-created roadmaps read back identically.
                    if (sr.resources() != null && !sr.resources().isEmpty()) {
                        r.setResources(objectMapper.writeValueAsString(sr.resources()));
                    }
                    roadmapRepository.save(r);
                    roadmapsCreated++;
                }
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("alumniCreated", alumniCreated);
        resp.put("roadmapsCreated", roadmapsCreated);
        resp.put("skipped", skipped);
        return resp;
    }
}