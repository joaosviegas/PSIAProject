package pt.psia.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.psia.profile.config.SecurityConfig;
import pt.psia.profile.service.ProfileService;
import pt.psia.profile.web.ProfileController;
import pt.psia.profile.web.dto.ProfileResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-secret-at-least-32-chars-long-ok")
class ProfileControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ProfileService profileService;

    @Test
    void putMeRequiresAuth() throws Exception {
        mvc.perform(put("/api/profiles/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Alice","bio":"hi"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putMeBlankDisplayNameRejected() throws Exception {
        mvc.perform(put("/api/profiles/me")
                        .with(jwt().jwt(j -> j.subject("00000000-0000-0000-0000-000000000001")
                                .claim("username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"","bio":"hi"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putMeDisplayNameTooLong() throws Exception {
        mvc.perform(put("/api/profiles/me")
                        .with(jwt().jwt(j -> j.subject("00000000-0000-0000-0000-000000000001")
                                .claim("username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"%s","bio":"hi"}""".formatted("A".repeat(51))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMeRequiresAuth() throws Exception {
        mvc.perform(get("/api/profiles/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeCreatesAnEmptyProfileForNewUsers() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(profileService.getOrCreate(id, "alice")).thenReturn(new ProfileResponse(
                id.toString(), "alice", "alice", null, List.of(), List.of(), List.of(), "2026-10-05T10:00:00Z"));

        mvc.perform(get("/api/profiles/me")
                        .with(jwt().jwt(j -> j.subject(id.toString()).claim("username", "alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.favoriteGenres.length()").value(0));
        verify(profileService).getOrCreate(id, "alice");
    }
}