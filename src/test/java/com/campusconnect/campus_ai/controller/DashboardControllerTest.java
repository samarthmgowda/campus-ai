package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role-aware dashboards. The composite X-Demo-User header decides which role
 * the caller is — and each dashboard refuses the wrong role, because a student
 * and a recruiter may share the same numeric id.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    // ——— student ———

    @Test
    void studentDashboardAcceptsStudentIdentity() throws Exception {
        when(dashboardService.getStudentDashboard(1L)).thenReturn(Map.of("identity", Map.of("name", "Asha")));

        mockMvc.perform(get("/dashboard/student")
                        .header("X-Demo-User", "student:1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.name").value("Asha"));

        verify(dashboardService).getStudentDashboard(1L);
    }

    /** A bare numeric header still resolves as a student (backward compatible). */
    @Test
    void studentDashboardAcceptsBareNumericHeader() throws Exception {
        when(dashboardService.getStudentDashboard(1L)).thenReturn(Map.of("identity", Map.of("name", "Asha")));

        mockMvc.perform(get("/dashboard/student")
                        .header("X-Demo-User", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(dashboardService).getStudentDashboard(1L);
    }

    @Test
    void studentDashboardRejectsRecruiterIdentity() throws Exception {
        mockMvc.perform(get("/dashboard/student")
                        .header("X-Demo-User", "recruiter:1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }

    @Test
    void studentDashboardRejectsMissingHeader() throws Exception {
        mockMvc.perform(get("/dashboard/student").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    // ——— alumni ———

    /** An alumni login needs no query param — the header supplies the id. */
    @Test
    void alumniDashboardFallsBackToHeaderAlumniId() throws Exception {
        when(dashboardService.getAlumniDashboard(eq(3L), isNull())).thenReturn(Map.of("identity", Map.of("name", "Ravi")));

        mockMvc.perform(get("/dashboard/alumni")
                        .header("X-Demo-User", "alumni:3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.name").value("Ravi"));

        verify(dashboardService).getAlumniDashboard(3L, null);
    }

    /** A student may view an alumni's public dashboard; the student id rides along. */
    @Test
    void alumniDashboardPassesStudentIdAsAdminContext() throws Exception {
        when(dashboardService.getAlumniDashboard(eq(3L), eq(7L))).thenReturn(Map.of());

        mockMvc.perform(get("/dashboard/alumni")
                        .param("alumniId", "3")
                        .header("X-Demo-User", "student:7")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(dashboardService).getAlumniDashboard(3L, 7L);
    }

    @Test
    void alumniDashboardWithoutIdOrAlumniIdentityIsBadRequest() throws Exception {
        mockMvc.perform(get("/dashboard/alumni")
                        .header("X-Demo-User", "student:7")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    // ——— recruiter ———

    @Test
    void recruiterDashboardAcceptsRecruiterIdentity() throws Exception {
        when(dashboardService.getRecruiterDashboard(2L)).thenReturn(Map.of("postings", Map.of("count", 4)));

        mockMvc.perform(get("/dashboard/recruiter")
                        .header("X-Demo-User", "recruiter:2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postings.count").value(4));

        verify(dashboardService).getRecruiterDashboard(2L);
    }

    @Test
    void recruiterDashboardRejectsStudentIdentity() throws Exception {
        mockMvc.perform(get("/dashboard/recruiter")
                        .header("X-Demo-User", "student:2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }

    @Test
    void recruiterDashboardRejectsMalformedHeader() throws Exception {
        mockMvc.perform(get("/dashboard/recruiter")
                        .header("X-Demo-User", "not-a-number")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }
}
