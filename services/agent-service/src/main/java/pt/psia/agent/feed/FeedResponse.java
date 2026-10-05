package pt.psia.agent.feed;

import pt.psia.agent.client.dto.MovieDto;

import java.time.Instant;
import java.util.List;

/**
 * What GET /api/feed returns. It is always renderable:
 * <ul>
 *   <li>{@code needs_preferences} — new account / no categories chosen yet: nothing to suggest, ask for categories;</li>
 *   <li>{@code building} — {@code picks} are instant heuristic picks, the AI version is being prepared in the background;</li>
 *   <li>{@code ready} — {@code picks} are the AI picks with their {@code summary}.</li>
 * </ul>
 * {@code discover} is the random half: movies from the chosen categories only.
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

    public static FeedResponse needsPreferences() {
        return new FeedResponse(NEEDS_PREFERENCES, null, null, false, List.of(), List.of(), List.of());
    }
}
