package pt.psia.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pt.psia.agent.config.SecurityConfig;
import pt.psia.agent.feed.Feed;
import pt.psia.agent.feed.FeedStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    FeedStore feedStore;

    @Test
    void withoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/feed")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsStoredFeed() throws Exception {
        when(feedStore.find(USER)).thenReturn(Optional.of(
                new Feed(USER, Instant.parse("2026-10-01T10:00:00Z"), "Enjoy", true, List.of(), List.of())));

        mvc.perform(get("/api/feed").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Enjoy"))
                .andExpect(jsonPath("$.aiGenerated").value(true));
        verify(feedStore, never()).markPending(any());
    }

    @Test
    void missingFeedIsMarkedPending() throws Exception {
        when(feedStore.find(USER)).thenReturn(Optional.empty());

        mvc.perform(get("/api/feed").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("pending"));
        verify(feedStore).markPending(USER);
    }
}
