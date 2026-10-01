package pt.psia.reco.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pt.psia.reco.config.SecurityConfig;
import pt.psia.reco.service.MovieService;
import pt.psia.reco.web.dto.MovieResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalMovieController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-secret-at-least-32-chars-long-ok")
class InternalMovieControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    MovieService movieService;

    private static MovieResponse movie(long id, String title) {
        return new MovieResponse(id, "tt" + id, title, 2000, List.of("Drama"), new BigDecimal("8.0"),
                1000, null, null, null, null, 120);
    }

    @Test
    void candidatesWithoutToken() throws Exception {
        when(movieService.candidates(List.of("Drama", "Sci-Fi"), Set.of(1L, 2L), 10))
                .thenReturn(List.of(movie(3, "Arrival")));

        mvc.perform(get("/internal/movies/candidates")
                        .param("genres", "Drama,Sci-Fi").param("exclude", "1,2").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Arrival"));
    }

    @Test
    void candidatesWithoutExclude() throws Exception {
        when(movieService.candidates(List.of("Drama"), Set.of(), 30)).thenReturn(List.of(movie(3, "Arrival")));

        mvc.perform(get("/internal/movies/candidates").param("genres", "Drama"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void byIdsWithoutToken() throws Exception {
        when(movieService.byIds(eq(List.of(4L, 5L)))).thenReturn(List.of(movie(4, "Heat"), movie(5, "Alien")));

        mvc.perform(get("/internal/movies").param("ids", "4,5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
