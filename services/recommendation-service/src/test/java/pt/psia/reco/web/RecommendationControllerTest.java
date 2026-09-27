package pt.psia.reco.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pt.psia.reco.config.SecurityConfig;
import pt.psia.reco.service.RecommendationService;
import pt.psia.reco.web.dto.MovieResponse;
import pt.psia.reco.web.dto.RecommendationResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-secret-at-least-32-chars-long-ok")
class RecommendationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    RecommendationService recommendationService;

    @Test
    void emptyRequestIsRejected() throws Exception {
        mvc.perform(post("/api/recommendations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutTokenIsRejected() throws Exception {
        mvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":[\"Comedy\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRequestReturnsRecommendations() throws Exception {
        MovieResponse movie = new MovieResponse(
                1L, "tt0111161", "The Shawshank Redemption", 1994,
                List.of("Drama"), new BigDecimal("9.3"), 2_800_000
        );
        when(recommendationService.recommend(any(), any()))
                .thenReturn(new RecommendationResponse("Great picks for a drama mood.", List.of(movie), true));

        mvc.perform(post("/api/recommendations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":[\"Drama\"],\"mood\":\"Something thought-provoking\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].title").value("The Shawshank Redemption"))
                .andExpect(jsonPath("$.aiGenerated").value(true));
    }
}
