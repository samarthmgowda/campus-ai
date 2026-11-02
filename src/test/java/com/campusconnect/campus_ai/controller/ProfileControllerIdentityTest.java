package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.CurrentUserService;
import com.campusconnect.campus_ai.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /me must resolve every role. Before role-aware identity it queried the
 * student table unconditionally, so an alumni or recruiter "sign-in" failed.
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

    @Test
    void studentMeReturnsAggregatedProfile() throws Exception {
        when(profileService.getMe(1L)).thenReturn(Map.of("profile", Map.of("name", "Asha")));

        mockMvc.perform(get("/me")
                        .header("X-Demo-User", "student:1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value("Asha"));

        verify(profileService).getMe(1L);
        verify(currentUserService, never()).resolveProfile(anyString());
    }

    @Test
    void alumniMeResolvesFromIdentityService() throws Exception {
        when(currentUserService.resolveProfile("alumni:3"))
                .thenReturn(Map.of("role", "alumni", "id", 3, "name", "Ravi"));

        mockMvc.perform(get("/me")
                        .header("X-Demo-User", "alumni:3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("alumni"))
                .andExpect(jsonPath("$.name").value("Ravi"));

        verify(currentUserService).resolveProfile("alumni:3");
        verify(profileService, never()).getMe(anyLong());
    }

    @Test
    void recruiterMeResolvesFromIdentityService() throws Exception {
        when(currentUserService.resolveProfile("recruiter:2"))
                .thenReturn(Map.of("role", "recruiter", "id", 2, "company", "Acme"));

        mockMvc.perform(get("/me")
                        .header("X-Demo-User", "recruiter:2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("recruiter"))
                .andExpect(jsonPath("$.company").value("Acme"));

        verify(profileService, never()).getMe(anyLong());
    }

    @Test
    void meWithoutHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(profileService);
    }
}
