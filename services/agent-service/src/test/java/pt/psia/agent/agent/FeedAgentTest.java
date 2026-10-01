package pt.psia.agent.agent;

import org.junit.jupiter.api.Test;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.ProfileDto;
import pt.psia.agent.feed.Feed;
import pt.psia.agent.feed.FeedBuilder;
import pt.psia.agent.feed.FeedStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeedAgentTest {

    private static final String A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String C = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    ProfileClient profileClient = mock(ProfileClient.class);
    FeedBuilder feedBuilder = mock(FeedBuilder.class);
    FeedStore feedStore = mock(FeedStore.class);
    FeedAgent agent = new FeedAgent(profileClient, feedBuilder, feedStore);

    @Test
    void buildsUpdatedAndPendingUsersAndSkipsFailures() {
        Instant since = Instant.parse("2026-10-01T10:00:00Z");
        ProfileDto a = profile(A), b = profile(B), c = profile(C);
        when(feedStore.lastRun()).thenReturn(Optional.of(since));
        when(profileClient.updatedSince(since)).thenReturn(List.of(a, b));
        when(feedStore.pending()).thenReturn(Set.of(B, C));
        when(profileClient.get(UUID.fromString(C))).thenReturn(c);
        when(feedBuilder.build(a)).thenThrow(new RuntimeException("boom"));
        when(feedBuilder.build(b)).thenReturn(feed(B));
        when(feedBuilder.build(c)).thenReturn(feed(C));

        agent.tick();

        verify(profileClient, never()).get(UUID.fromString(B));
        verify(feedStore).save(feed(B));
        verify(feedStore).save(feed(C));
        verify(feedStore).clearPending(B);
        verify(feedStore).clearPending(C);
        verify(feedStore, never()).clearPending(A);
        verify(feedStore).setLastRun(any());
    }

    @Test
    void doesNotAdvanceLastRunWhenProfileServiceIsDown() {
        when(feedStore.lastRun()).thenReturn(Optional.empty());
        when(profileClient.updatedSince(null)).thenThrow(new RuntimeException("connection refused"));

        agent.tick();

        verify(feedStore, never()).setLastRun(any());
        verify(feedStore, never()).save(any());
    }

    @Test
    void dropsPendingUserWithoutProfile() {
        when(feedStore.lastRun()).thenReturn(Optional.empty());
        when(profileClient.updatedSince(null)).thenReturn(List.of());
        when(feedStore.pending()).thenReturn(Set.of(C));
        when(profileClient.get(UUID.fromString(C))).thenThrow(new RuntimeException("404"));

        agent.tick();

        verify(feedStore).clearPending(C);
        verify(feedStore, never()).save(any());
    }

    private static ProfileDto profile(String id) {
        return new ProfileDto(id, id, id, null, List.of(), List.of(), List.of(), null);
    }

    private static Feed feed(String id) {
        return new Feed(id, Instant.EPOCH, "s", true, List.of(), List.of());
    }
}
