package pt.psia.agent.feed;

import pt.psia.agent.client.dto.MovieDto;

import java.time.Instant;
import java.util.List;

public record Feed(
        String userId,
        Instant generatedAt,
        String summary,
        boolean aiGenerated,
        List<MovieDto> picks,
        List<SimilarPick> fromSimilarUsers
) {
    public record SimilarPick(MovieDto movie, List<String> likedBy) {}
}
