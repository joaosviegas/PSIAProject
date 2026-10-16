package pt.psia.agent.feed;

import pt.psia.agent.client.dto.MovieDto;

import java.time.Instant;
import java.util.List;

/**
 * A response to a feed request.
 */
public record FeedResponse(
        String status,
        Instant generatedAt,
        String summary,
        boolean aiGenerated,
        List<MovieDto> picks,
        List<MovieDto> discover,
        List<Feed.SimilarPick> fromSimilarUsers
) {
    public static final String NEEDS_PREFERENCES = "needs_preferences";
    public static final String BUILDING = "building";
    public static final String READY = "ready";

    /**
     * Returns a feed response indicating that the feed is being built.
     */
    public static FeedResponse needsPreferences() {
        return new FeedResponse(NEEDS_PREFERENCES, null, null, false, List.of(), List.of(), List.of());
    }
}
