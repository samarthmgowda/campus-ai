package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.ProjectSubmission;
import com.campusconnect.campus_ai.repository.ProjectSubmissionRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TEMPORARY - remove after demo
 *
 * Seeds the submission corpus so the cross-submission originality check has
 * something to compare against.
 *
 * <p>These bodies are written on purpose to exercise the whole verdict range:
 * one pair that is a near-verbatim copy (should read HIGH), one that shares a
 * topic and some phrasing but is its own work (MODERATE at most, likely LOW),
 * and one on an unrelated topic (NONE). A seed that only produced matches would
 * leave the "no overlap" path untested and would make every demo look
 * alarming.
 *
 * <p>Idempotent by title, like DevSeedProjectsController — re-running the seed
 * must not quietly double the corpus, because a doubled corpus changes what
 * every score means.
 */
@RestController
@RequestMapping("/dev")
public class DevSeedSubmissionsController {

    private final ProjectSubmissionRepository submissionRepository;

    public DevSeedSubmissionsController(ProjectSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    private record SeedSubmission(String title, String userId, String text) {}

    /**
     * The copied pair. B is A with a handful of words swapped and one sentence
     * dropped — the exact shape a student produces when they "rewrite" a
     * friend's submission, and the shape word-overlap catches well.
     */
    private static final String ORIGINAL_BODY = """
            Introduction. Campus placement season has become the single most stressful period in an \
            engineering student's life. Students apply to dozens of companies, attend back-to-back \
            aptitude tests, and still struggle to convert interviews into offers. The core problem is \
            not a lack of opportunities but a lack of preparation that matches what recruiters actually \
            test. Most students prepare from whatever material they find online, which is rarely aligned \
            with the hiring pattern of the companies visiting their own campus.

            Problem statement. We propose a placement readiness platform that collects previous \
            interview experiences from seniors, tags each experience with the company, role, and round \
            type, and uses that tagged corpus to generate a personalised preparation plan for every \
            student. The plan adapts as the student completes mock tests, so the next recommendation \
            reflects the weakest area rather than a fixed syllabus.

            Methodology. Data was collected through a structured form circulated to final year students \
            and recent graduates. Each response was manually cleaned and tagged. We then computed a \
            difficulty score per round type and mapped it to the student's practice history.

            Results. In a pilot with sixty students, the average number of interview rounds cleared \
            rose from one point eight to two point six. Students reported higher confidence and spent \
            measurably less time searching for preparation material.

            Conclusion. Aligning preparation material with the actual hiring pattern of on-campus \
            recruiters produces a measurable improvement in interview outcomes. The approach is cheap \
            to run because it reuses experience data the institution already possesses.
            """;

    private static final String REWRITTEN_BODY = """
            Introduction. Placement season has become the most stressful period in an engineering \
            student's life. Students apply to dozens of companies, attend back to back aptitude \
            tests, and still struggle to convert interviews into offers. The core problem is not a \
            lack of opportunities but a lack of preparation that matches what recruiters actually \
            test. Most students prepare from whatever material they find online, which is rarely \
            aligned with the hiring pattern of the companies visiting their own campus.

            Problem statement. We propose a placement readiness platform that collects previous \
            interview experiences from seniors, tags each experience with the company, role and \
            round type, and uses that tagged corpus to generate a personalised preparation plan for \
            every student. The plan adapts as the student completes mock tests, so the next \
            recommendation reflects the weakest area rather than a fixed syllabus.

            Methodology. Data was collected through a structured form circulated to final year \
            students and recent graduates. Each response was manually cleaned and tagged. We then \
            computed a difficulty score per round type and mapped it to the student's practice \
            history.

            Danger: this submission never mentions results, so it is not a perfect copy.
            """;

    /** Same field, different work — the case that must NOT read HIGH. */
    private static final String INDEPENDENT_BODY = """
            Abstract. This report studies whether attendance tracking based on Bluetooth low energy \
            beacons can replace manual roll calls in large lecture halls. Manual roll calls consume \
            instructional time and are easy to proxy, while biometric systems raise consent concerns \
            that a department cannot answer on its own.

            Design. Each classroom was fitted with a single beacon broadcasting a rotating identifier. \
            A student's phone resolved the identifier and submitted a signed attendance token. \
            Rotating the identifier every thirty seconds stopped students from sharing a token with a \
            friend who never entered the room.

            Evaluation. Over one semester the system recorded attendance for eleven courses. The \
            recorded count matched a manual headcount within two students in every session, and \
            instructional time recovered averaged four minutes per lecture.

            Limitations. Beacon range crosses thin walls, so two adjacent classrooms occasionally \
            captured the same student. We did not attempt to detect phones left in a bag, which \
            remains the main way a student can be marked present without attending.

            Conclusion. Beacon based attendance is accurate enough to replace roll calls when the \
            beacon identifier rotates, and it avoids the consent problems of biometric capture.
            """;

    @PostMapping("/seed-submissions")
    public Map<String, Object> seedSubmissions() {
        List<SeedSubmission> data = List.of(
                new SeedSubmission("Placement Readiness Platform — final report",
                        "aarav.sharma@campus.edu", ORIGINAL_BODY),
                new SeedSubmission("Placement Readiness Platform — final report (resubmission)",
                        "rohan.verma@campus.edu", REWRITTEN_BODY),
                new SeedSubmission("Beacon Attendance System — final report",
                        "vikram.patel@campus.edu", INDEPENDENT_BODY));

        Map<String, ProjectSubmission> byTitle = new HashMap<>();
        for (ProjectSubmission s : submissionRepository.findAll()) {
            if (s.getTitle() != null) byTitle.put(s.getTitle(), s);
        }

        int created = 0;
        List<String> skipped = new ArrayList<>();

        for (SeedSubmission seed : data) {
            if (byTitle.containsKey(seed.title())) {
                skipped.add(seed.title());
                continue;
            }
            ProjectSubmission s = new ProjectSubmission();
            s.setTitle(seed.title());
            s.setSubmissionText(seed.text());
            s.setUserId(seed.userId());
            s.setStatus("SUBMITTED");
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            submissionRepository.save(s);
            created++;
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("submissionsCreated", created);
        resp.put("skipped", skipped);
        resp.put("totalStored", submissionRepository.count());
        return resp;
    }
}
