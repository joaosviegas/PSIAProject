package pt.psia.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pt.psia.agent.client.dto.MovieDto;
import pt.psia.agent.config.SecurityConfig;
import pt.psia.agent.feed.FeedResponse;
import pt.psia.agent.feed.FeedService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-secret-at-least-32-chars-long-ok")
class FeedControllerTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    FeedService feedService;

    @Test
    void withoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/feed")).andExpect(status().isUnauthorized());
    }

    @Test
    void readyFeedIsReturned() throws Exception {
        when(feedService.get(USER)).thenReturn(new FeedResponse(FeedResponse.READY,
                Instant.parse("2026-10-01T10:00:00Z"), "Enjoy", true, List.of(), List.of(), List.of()));

        mvc.perform(get("/api/feed").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.summary").value("Enjoy"))
                .andExpect(jsonPath("$.aiGenerated").value(true));
    }

    @Test
    void newAccountIsAskedForPreferencesInsteadOfGettingAnEmptyFeed() throws Exception {
        when(feedService.get(USER)).thenReturn(FeedResponse.needsPreferences());

        mvc.perform(get("/api/feed").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("needs_preferences"))
                .andExpect(jsonPath("$.picks.length()").value(0));
    }

    @Test
    void refreshReturnsImmediatelyAndStartsTheBuild() throws Exception {
        mvc.perform(post("/api/feed/refresh").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("building"));
        verify(feedService).refresh(USER);
    }

    @Test
    void discoverReturnsMovies() throws Exception {
        when(feedService.discover(USER)).thenReturn(List.of(
                new MovieDto(5L, null, "Heat", 1995, List.of("Crime"), null, null, null, null, null, null, null)));

        mvc.perform(get("/api/feed/discover").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Heat"));
    }
}
