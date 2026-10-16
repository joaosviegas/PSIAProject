package pt.psia.reco.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the moods offered by the "Ask AI" form to genres, so a mood actually steers
 * the (instant) ranking instead of relying only on a small language model to interpret it.
 */
public final class Moods {

    /** Mapping of moods to corresponding IMDb genres. */
    private static final Map<String, List<String>> GENRES = Map.of(
            "something uplifting", List.of("Comedy", "Family", "Animation", "Musical", "Music", "Adventure"),
            "something intense", List.of("Thriller", "Action", "Crime", "War", "Mystery"),
            "something to relax to", List.of("Comedy", "Family", "Animation", "Romance", "Fantasy", "Music"),
            "something thought-provoking", List.of("Drama", "Mystery", "Sci-Fi", "Documentary", "Biography", "History"),
            "something scary", List.of("Horror", "Thriller", "Mystery"),
            "something funny", List.of("Comedy", "Animation", "Family")
    );

    /** Returns the list of genres corresponding to the given mood. */
    public static List<String> genresFor(String mood) {
        if (mood == null || mood.isBlank()) {
            return List.of();
        }
        return GENRES.getOrDefault(mood.trim().toLowerCase(Locale.ROOT), List.of());
    }

    /** Private constructor to prevent instantiation. */
    private Moods() {
    }
}
