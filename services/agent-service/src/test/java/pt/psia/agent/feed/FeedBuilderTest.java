package pt.psia.agent.feed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.psia.agent.client.MovieClient;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.MovieDto;
import pt.psia.agent.client.dto.ProfileDto;
import pt.psia.agent.llm.LlmClient;
import pt.psia.agent.llm.LlmPicks;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedBuilderTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    ProfileClient profileClient = mock(ProfileClient.class);
    MovieClient movieClient = mock(MovieClient.class);
    LlmClient llmClient = mock(LlmClient.class);
    FeedBuilder builder = new FeedBuilder(profileClient, movieClient, llmClient);

    ProfileDto me = new ProfileDto(USER, "me", "Me", "likes space", List.of("Sci-Fi"), List.of(1L), List.of(2L), null);

    @BeforeEach
    void setUp() {
        when(movieClient.candidates(eq(List.of("Sci-Fi")), eq(Set.of(1L, 2L)), anyInt()))
                .thenReturn(List.of(movie(10, "Arrival"), movie(11, "Alien"), movie(12, "Contact")));
        when(movieClient.byIds(any())).thenAnswer(inv -> ((Collection<Long>) inv.getArgument(0)).stream()
                .map(id -> movie(id, "Movie " + id)).toList());
        when(profileClient.similar(UUID.fromString(USER), 5)).thenReturn(List.of());
    }

    @Test
    void matchesLlmPicksToCandidates() {
        when(llmClient.pickMovies(anyString())).thenReturn(new LlmPicks("Space!", List.of("alien ", "Not a candidate", "Arrival (2000)")));

        Feed feed = builder.build(me);

        assertThat(feed.aiGenerated()).isTrue();
        assertThat(feed.summary()).isEqualTo("Space!");
        assertThat(feed.picks()).extracting(MovieDto::title).containsExactly("Alien", "Arrival");
    }

    @Test
    void fallsBackWhenLlmFails() {
        when(llmClient.pickMovies(anyString())).thenThrow(new RuntimeException("connection refused"));

        Feed feed = builder.build(me);

        assertThat(feed.aiGenerated()).isFalse();
        assertThat(feed.summary()).isEqualTo(FeedBuilder.FALLBACK_SUMMARY);
        assertThat(feed.picks()).extracting(MovieDto::id).containsExactly(10L, 11L, 12L);
    }

    @Test
    void similarUsersPicksExcludeSeenAndAiPicksAndRankByCount() {
        when(llmClient.pickMovies(anyString())).thenReturn(new LlmPicks("Space!", List.of("Arrival")));
        when(profileClient.similar(UUID.fromString(USER), 5)).thenReturn(List.of(
                new ProfileDto("a", "ana", "Ana", null, List.of(), List.of(1L, 2L, 10L, 20L, 30L), List.of(), null),
                new ProfileDto("b", "bob", "Bob", null, List.of(), List.of(30L), List.of(), null)));

        Feed feed = builder.build(me);

        assertThat(feed.fromSimilarUsers()).extracting(p -> p.movie().id()).containsExactly(30L, 20L);
        assertThat(feed.fromSimilarUsers().get(0).likedBy()).containsExactly("Ana", "Bob");
    }

    private static MovieDto movie(long id, String title) {
        return new MovieDto(id, null, title, 2000, List.of("Sci-Fi"), null, null, null, null, null, null, null);
    }
}
