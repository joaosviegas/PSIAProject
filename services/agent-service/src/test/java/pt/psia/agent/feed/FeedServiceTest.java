package pt.psia.agent.feed;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import pt.psia.agent.client.MovieClient;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.MovieDto;
import pt.psia.agent.client.dto.ProfileDto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class FeedServiceTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String VERSION = "2026-10-05T10:00:00Z";

    ProfileClient profileClient = mock(ProfileClient.class);
    MovieClient movieClient = mock(MovieClient.class);
    FeedBuilder feedBuilder = mock(FeedBuilder.class);
    FeedStore feedStore = mock(FeedStore.class);
    FeedService service = new FeedService(profileClient, movieClient, feedBuilder, feedStore, new SimpleMeterRegistry());

    ProfileDto withGenres = new ProfileDto(USER, "me", "Me", null, List.of("Drama"), List.of(1L), List.of(2L), VERSION);

    @Test
    void userWithoutProfileIsAskedForPreferences() {
        when(profileClient.find(UUID.fromString(USER))).thenReturn(Optional.empty());

        FeedResponse response = service.get(USER);

        assertThat(response.status()).isEqualTo(FeedResponse.NEEDS_PREFERENCES);
        assertThat(response.picks()).isEmpty();
        verifyNoInteractions(movieClient, feedBuilder);
    }

    @Test
    void userWithoutCategoriesIsAskedForPreferences() {
        when(profileClient.find(UUID.fromString(USER))).thenReturn(Optional.of(
                new ProfileDto(USER, "me", "Me", null, List.of(), List.of(1L), List.of(), VERSION)));

        assertThat(service.get(USER).status()).isEqualTo(FeedResponse.NEEDS_PREFERENCES);
        verifyNoInteractions(movieClient, feedBuilder);
    }

    @Test
    void upToDateFeedIsReadyAndNotRebuilt() {
        Feed feed = new Feed(USER, Instant.parse("2026-10-05T10:01:00Z"), "Enjoy", true,
                List.of(movie(10, "Arrival")), List.of(), VERSION);
        when(profileClient.find(UUID.fromString(USER))).thenReturn(Optional.of(withGenres));
        when(feedStore.find(USER)).thenReturn(Optional.of(feed));
        when(movieClient.discover(anyCollection(), anyCollection(), anyInt())).thenReturn(List.of(movie(20, "Heat")));

        FeedResponse response = service.get(USER);

        assertThat(response.status()).isEqualTo(FeedResponse.READY);
        assertThat(response.aiGenerated()).isTrue();
        assertThat(response.picks()).extracting(MovieDto::title).containsExactly("Arrival");
        assertThat(response.discover()).extracting(MovieDto::title).containsExactly("Heat");
        verifyNoInteractions(feedBuilder);
    }

    @Test
    void staleFeedShowsInstantPicksAndRebuildsInTheBackground() {
        Feed stale = new Feed(USER, Instant.EPOCH, "Old", true, List.of(movie(99, "Old pick")), List.of(), "older-version");
        when(profileClient.find(UUID.fromString(USER))).thenReturn(Optional.of(withGenres));
        when(feedStore.find(USER)).thenReturn(Optional.of(stale));
        when(movieClient.personalized(List.of("Drama"), List.of(1L), List.of(2L), FeedService.QUICK_PICKS))
                .thenReturn(List.of(movie(10, "Arrival")));
        when(movieClient.discover(anyCollection(), anyCollection(), anyInt())).thenReturn(List.of(movie(20, "Heat")));
        when(feedBuilder.build(withGenres)).thenReturn(stale);

        FeedResponse response = service.get(USER);

        assertThat(response.status()).isEqualTo(FeedResponse.BUILDING);
        assertThat(response.aiGenerated()).isFalse();
        assertThat(response.picks()).extracting(MovieDto::title).containsExactly("Arrival");
        assertThat(response.discover()).isNotEmpty();
        verify(feedBuilder, timeout(2000)).build(withGenres);
        verify(feedStore, timeout(2000)).save(any());
    }

    @Test
    void refreshStartsABuildOnlyForUsersWithCategories() {
        when(profileClient.find(UUID.fromString(USER))).thenReturn(Optional.of(withGenres));
        when(feedBuilder.build(withGenres)).thenThrow(new RuntimeException("boom"));

        service.refresh(USER);

        verify(feedBuilder, timeout(2000)).build(withGenres);
    }

    @Test
    void discoverIsEmptyUntilCategoriesAreChosen() {
        when(profileClient.find(UUID.fromString(USER))).thenReturn(Optional.empty());

        assertThat(service.discover(USER)).isEmpty();
    }

    private static MovieDto movie(long id, String title) {
        return new MovieDto(id, null, title, 2000, List.of("Drama"), null, null, null, null, null, null, null);
    }
}
