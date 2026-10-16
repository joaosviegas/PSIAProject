package pt.psia.agent.feed;

import pt.psia.agent.client.dto.MovieDto;

import java.time.Instant;
import java.util.List;

/**
 * A feed of personalized movie recommendations.
 */
public record Feed(
        String userId,
        Instant generatedAt,
        String summary,
        boolean aiGenerated,
        List<MovieDto> picks,
        List<SimilarPick> fromSimilarUsers,
        /** The profile's updatedAt this feed was built from; a different value means the feed is stale. */
        String profileVersion
) {
    public record SimilarPick(MovieDto movie, List<String> likedBy) {}
}
