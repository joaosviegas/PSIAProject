package pt.psia.agent.agent;

import org.junit.jupiter.api.Test;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.ProfileDto;
import pt.psia.agent.feed.FeedService;
import pt.psia.agent.feed.FeedStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeedAgentTest {

    ProfileClient profileClient = mock(ProfileClient.class);
    FeedService feedService = mock(FeedService.class);
    FeedStore feedStore = mock(FeedStore.class);
    FeedAgent agent = new FeedAgent(profileClient, feedService, feedStore);

    @Test
    void queuesRebuildsForUpdatedProfilesAndAdvancesLastRun() {
        Instant since = Instant.parse("2026-10-01T10:00:00Z");
        ProfileDto a = profile("a"), b = profile("b");
        when(feedStore.lastRun()).thenReturn(Optional.of(since));
        when(profileClient.updatedSince(since)).thenReturn(List.of(a, b));

        agent.tick();

        verify(feedService).rebuildAsync(a);
        verify(feedService).rebuildAsync(b);
        verify(feedStore).setLastRun(any());
    }

    @Test
    void doesNotAdvanceLastRunWhenProfileServiceIsDown() {
        when(feedStore.lastRun()).thenReturn(Optional.empty());
        when(profileClient.updatedSince(null)).thenThrow(new RuntimeException("connection refused"));

        agent.tick();

        verify(feedStore, never()).setLastRun(any());
        verifyNoInteractions(feedService);
    }

    private static ProfileDto profile(String id) {
        return new ProfileDto(id, id, id, null, List.of("Drama"), List.of(), List.of(), null);
    }
}
