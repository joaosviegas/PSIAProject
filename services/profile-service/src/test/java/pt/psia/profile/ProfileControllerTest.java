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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

}
