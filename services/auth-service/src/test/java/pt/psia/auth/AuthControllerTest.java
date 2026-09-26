package pt.psia.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.psia.auth.config.SecurityConfig;
import pt.psia.auth.service.AuthService;
import pt.psia.auth.web.AuthController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-secret-at-least-32-chars-long-ok")
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    AuthService authService;

    @Test
    void registerValidationUsernameTooShort() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"password123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerValidationPasswordTooShort() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"short"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerValidationBadCharsInUsername() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"al!ce","password":"password123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginValidationBlankUsername() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"password123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meRequiresAuth() throws Exception {
        mvc.perform(post("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
