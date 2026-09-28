package pt.psia.reco.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pt.psia.reco.config.SecurityConfig;
import pt.psia.reco.service.MovieService;
import pt.psia.reco.web.dto.MovieResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovieController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-secret-at-least-32-chars-long-ok")
class MovieControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    MovieService movieService;

    @Test
    void searchWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/movies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchReturnsMoviesWhenAuthenticated() throws Exception {
        MovieResponse movie = new MovieResponse(
                1L, "tt0111161", "The Shawshank Redemption", 1994,
                List.of("Drama"), new BigDecimal("9.3"), 2_800_000,
                "A banker is sentenced to life in Shawshank.",
                "https://image.tmdb.org/t/p/w342/poster.jpg",
                "Frank Darabont", "Tim Robbins|Morgan Freeman", 142
        );
        when(movieService.search(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(movie)));

        mvc.perform(get("/api/movies").param("query", "shawshank").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("The Shawshank Redemption"));
    }

    @Test
    void genresListDoesNotRequireQueryParams() throws Exception {
        mvc.perform(get("/api/movies/genres").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Action"));
    }
}
