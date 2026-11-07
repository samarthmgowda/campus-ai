package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role-aware dashboards. The acting role now comes from the JWT principal
 * (set here with {@link TestAuth}) rather than a client-supplied header — a
 * caller can no longer ask for the wrong dashboard by typing a different role.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    // The JWT filter is auto-registered in the web slice; it needs a JwtService
    // even though these tests set the principal directly via TestAuth.
    @MockitoBean
    private com.campusconnect.campus_ai.service.JwtService jwtService;

    @AfterEach
    void clearCaller() {
        TestAuth.clear();
    }

    // ——— student ———

    @Test
    void studentDashboardAcceptsStudentIdentity() throws Exception {
        TestAuth.asStudent(1L);
        when(dashboardService.getStudentDashboard(1L)).thenReturn(Map.of("identity", Map.of("name", "Asha")));

        mockMvc.perform(get("/dashboard/student").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.name").value("Asha"));

        verify(dashboardService).getStudentDashboard(1L);
    }

    @Test
    void studentDashboardRejectsRecruiterIdentity() throws Exception {
        TestAuth.asRecruiter(1L);

        mockMvc.perform(get("/dashboard/student").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }

    @Test
    void studentDashboardRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/dashboard/student").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dashboardService);
    }

    // ——— alumni ———

    /** An alumni login needs no query param — the principal supplies the id. */
    @Test
    void alumniDashboardFallsBackToPrincipalAlumniId() throws Exception {
        TestAuth.asAlumni(3L);
        when(dashboardService.getAlumniDashboard(eq(3L), isNull())).thenReturn(Map.of("identity", Map.of("name", "Ravi")));

        mockMvc.perform(get("/dashboard/alumni").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.name").value("Ravi"));

        verify(dashboardService).getAlumniDashboard(3L, null);
    }

    /** A student may view an alumni's public dashboard; the student id rides along. */
    @Test
    void alumniDashboardPassesStudentIdAsContext() throws Exception {
        TestAuth.asStudent(7L);
        when(dashboardService.getAlumniDashboard(eq(3L), eq(7L))).thenReturn(Map.of());

        mockMvc.perform(get("/dashboard/alumni")
                        .param("alumniId", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(dashboardService).getAlumniDashboard(3L, 7L);
    }

    @Test
    void alumniDashboardWithoutIdOrAlumniIdentityIsBadRequest() throws Exception {
        TestAuth.asStudent(7L);

        mockMvc.perform(get("/dashboard/alumni").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    // ——— recruiter ———

    @Test
    void recruiterDashboardAcceptsRecruiterIdentity() throws Exception {
        TestAuth.asRecruiter(2L);
        when(dashboardService.getRecruiterDashboard(2L)).thenReturn(Map.of("postings", Map.of("count", 4)));

        mockMvc.perform(get("/dashboard/recruiter").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postings.count").value(4));

        verify(dashboardService).getRecruiterDashboard(2L);
    }

    @Test
    void recruiterDashboardRejectsStudentIdentity() throws Exception {
        TestAuth.asStudent(2L);

        mockMvc.perform(get("/dashboard/recruiter").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }
}
