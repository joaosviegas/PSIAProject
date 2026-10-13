package pt.psia.profile.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.psia.profile.config.SecurityConfig;
import pt.psia.profile.service.ProfileService;
import pt.psia.profile.web.dto.PageResponse;
import pt.psia.profile.web.dto.ProfileResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalProfileController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-secret-at-least-32-chars-long-ok")
class InternalProfileControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ProfileService profileService;

    @Test
    void getProfileByIdAllowedWithoutToken() throws Exception {
        UUID userId = UUID.randomUUID();
        ProfileResponse response = new ProfileResponse(
                userId.toString(), "alice", "Alice", "bio",
                List.of("Drama"), List.of(1), List.of(), "2026-10-01T10:00:00Z"
        );
        when(profileService.getProfile(userId)).thenReturn(response);

        mvc.perform(get("/internal/profiles/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("Alice"));
    }

    @Test
    void findUpdatedSinceReturnsPage() throws Exception {
        ProfileResponse response = new ProfileResponse(
                UUID.randomUUID().toString(), "bob", "Bob", null,
                List.of(), List.of(), List.of(), "2026-10-05T12:00:00Z"
        );
        PageResponse<ProfileResponse> page = new PageResponse<>(List.of(response), 0, 20, 1, 1);
        when(profileService.findUpdatedSince(any(Instant.class), eq(0), eq(20))).thenReturn(page);

        mvc.perform(get("/internal/profiles").param("updatedSince", "2026-10-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("bob"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void findSimilarReturnsList() throws Exception {
        UUID userId = UUID.randomUUID();
        ProfileResponse similar = new ProfileResponse(
                UUID.randomUUID().toString(), "charlie", "Charlie", null,
                List.of("Sci-Fi"), List.of(), List.of(), "2026-10-05T12:00:00Z"
        );
        when(profileService.findSimilar(userId, 5)).thenReturn(List.of(similar));

        mvc.perform(get("/internal/profiles/similar/" + userId).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("charlie"));
    }
}