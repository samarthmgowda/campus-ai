package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.enums.UserRole;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.CurrentUser;
import com.campusconnect.campus_ai.service.CurrentUserService;
import com.campusconnect.campus_ai.service.ProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /me must resolve every role. Before role-aware identity it queried the
 * student table unconditionally, so an alumni or recruiter "sign-in" failed.
 * The acting role now comes from the JWT principal, not a header.
 */
@WebMvcTest(ProfileController.class)
class ProfileControllerIdentityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private StudentRepository studentRepository;

    // The JWT filter is auto-registered in the web slice; it needs a JwtService
    // even though these tests set the principal directly via TestAuth.
    @MockitoBean
    private com.campusconnect.campus_ai.service.JwtService jwtService;

    @AfterEach
    void clearCaller() {
        TestAuth.clear();
    }

    @Test
    void studentMeReturnsAggregatedProfile() throws Exception {
        TestAuth.asStudent(1L);
        when(profileService.getMe(1L)).thenReturn(Map.of("profile", Map.of("name", "Asha")));

        mockMvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value("Asha"));

        verify(profileService).getMe(1L);
        verify(currentUserService, never()).resolveProfile(any(CurrentUser.class));
    }

    @Test
    void alumniMeResolvesFromIdentityService() throws Exception {
        TestAuth.asAlumni(3L);
        when(currentUserService.resolveProfile(any(CurrentUser.class)))
                .thenReturn(Map.of("role", "alumni", "id", 3, "name", "Ravi"));

        mockMvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("alumni"))
                .andExpect(jsonPath("$.name").value("Ravi"));

        verify(currentUserService).resolveProfile(any(CurrentUser.class));
        verify(profileService, never()).getMe(anyLong());
    }

    @Test
    void recruiterMeResolvesFromIdentityService() throws Exception {
        TestAuth.asRecruiter(2L);
        when(currentUserService.resolveProfile(any(CurrentUser.class)))
                .thenReturn(Map.of("role", "recruiter", "id", 2, "company", "Acme"));

        mockMvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("recruiter"))
                .andExpect(jsonPath("$.company").value("Acme"));

        verify(profileService, never()).getMe(anyLong());
    }

    /** An admin account with no bound profile row gets a minimal identity, not a 404. */
    @Test
    void adminMeReturnsMinimalIdentity() throws Exception {
        TestAuth.asAdmin();

        mockMvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));

        verifyNoInteractions(profileService);
        verify(currentUserService, never()).resolveProfile(any(CurrentUser.class));
    }

    @Test
    void meWithoutPrincipalIsRejected() throws Exception {
        mockMvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(profileService);
    }
}
