package pt.psia.reco.service;

import pt.psia.reco.domain.Movie;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for scoring and ranking movies based on user taste and preferences.
 */
public final class MovieScorer {

    static final double PRIMARY_WEIGHT = 1.5;
    static final double SECONDARY_WEIGHT = 0.7;
    static final double LIKED_STEP = 0.35;
    static final double LIKED_CAP = 1.5;
    static final double DISLIKED_STEP = -0.5;
    static final double DISLIKED_FLOOR = -2.0;

    private static final double PRIOR_VOTES = 2500;
    private static final double PRIOR_MEAN = 6.5;
    private static final Pattern NAME_SPLIT = Pattern.compile("\\s*[|,;]\\s*");

    /** 
     * Represents a user's taste profile, including genre weights, preferred directors and stars, and median year of liked movies.
     * @param genreWeights A map of genres to their corresponding weights based on user preferences.
     * @param directors A set of preferred directors based on liked movies.
     * @param stars A set of preferred stars based on liked movies.
     * @param medianYear The median year of the user's liked movies, used for era-based scoring.
     */
    public record Taste(Map<String, Double> genreWeights, Set<String> directors, Set<String> stars, Integer medianYear) {
    }

    /** Constructs a Taste profile based on the user's primary and secondary genre preferences, liked movies, and disliked movies. */
    public static Taste taste(Collection<String> primary, Collection<String> secondary,
                              Collection<Movie> liked, Collection<Movie> disliked) {
        Map<String, Double> weights = new HashMap<>();
        secondary.forEach(g -> weights.merge(g, SECONDARY_WEIGHT, Math::max));
        primary.forEach(g -> weights.merge(g, PRIMARY_WEIGHT, Math::max));

        Map<String, Double> likedBoost = new HashMap<>();
        for (Movie m : liked) {
            m.getGenreList().forEach(g -> likedBoost.merge(g, LIKED_STEP, Double::sum));
        }
        likedBoost.forEach((g, v) -> weights.merge(g, Math.min(v, LIKED_CAP), Double::sum));

        Map<String, Double> dislikedPenalty = new HashMap<>();
        for (Movie m : disliked) {
            m.getGenreList().forEach(g -> dislikedPenalty.merge(g, DISLIKED_STEP, Double::sum));
        }
        dislikedPenalty.forEach((g, v) -> weights.merge(g, Math.max(v, DISLIKED_FLOOR), Double::sum));

        Set<String> directors = new HashSet<>();
        Set<String> stars = new HashSet<>();
        List<Integer> years = new ArrayList<>();
        for (Movie m : liked) {
            directors.addAll(names(m.getDirectors()));
            stars.addAll(names(m.getStars()));
            if (m.getStartYear() != null) {
                years.add(m.getStartYear());
            }
        }
        Collections.sort(years);
        Integer median = years.isEmpty() ? null : years.get(years.size() / 2);
        return new Taste(weights, directors, stars, median);
    }

    /** Ranks the given pool of movies based on the user's taste profile, returning the top {@code limit} movies. */
    public static List<Movie> rank(Collection<Movie> pool, Taste taste, int limit) {
        return pool.stream()
                .sorted(Comparator
                        .comparingDouble((Movie m) -> -score(m, taste))
                        .thenComparing(m -> -(m.getNumVotes() == null ? 0 : m.getNumVotes())))
                .limit(limit)
                .toList();
    }

    /** The {@code n} most frequent genres among the given movies. */
    public static List<String> topGenres(Collection<Movie> movies, int n) {
        Map<String, Integer> counts = new HashMap<>();
        movies.forEach(m -> m.getGenreList().forEach(g -> counts.merge(g, 1, Integer::sum)));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Computes a score for a movie based on the user's taste profile, considering genre affinity, quality, popularity, people involved, and era. */
    static double score(Movie m, Taste t) {
        List<String> genres = m.getGenreList();
        // Average weight of the movie's genres according to the user's taste, normalized by the number of genres.
        double affinity = 0;
        for (String g : genres) {
            affinity += t.genreWeights().getOrDefault(g, 0.0);
        }
        affinity = genres.isEmpty() ? 0 : affinity / Math.sqrt(genres.size());

        // Quality is based on a weighted rating that combines the movie's average rating and number of votes, normalized to a 0-1 scale.
        double quality = clamp((weightedRating(m) - 5.0) / 4.0);
        // Popularity is based on the logarithm of the number of votes, normalized to a 0-1 scale.
        double popularity = clamp(Math.log10((m.getNumVotes() == null ? 0 : m.getNumVotes()) + 1) / 6.5);

        // People score is based on the overlap of directors and stars with the user's preferred directors and stars, with a maximum contribution of 1.2.
        double people = 0;
        if (names(m.getDirectors()).stream().anyMatch(t.directors()::contains)) {
            people += 0.6;
        }
        // Count shared stars and add to the people score, with a maximum contribution of 0.6.
        long sharedStars = names(m.getStars()).stream().filter(t.stars()::contains).count();
        people += Math.min(0.6, sharedStars * 0.2);

        // Era score is based on how close the movie's release year is to the median year of the user's liked movies, with a maximum contribution of 1.0.
        double era = 0;
        if (t.medianYear() != null && m.getStartYear() != null) {
            era = clamp(1 - Math.abs(m.getStartYear() - t.medianYear()) / 40.0);
        }

        // Final score is a weighted sum of the individual components, reflecting the user's taste and preferences.
        return 2.0 * affinity + 1.6 * quality + 0.4 * popularity + 1.0 * people + 0.3 * era;
    }

    /** Computes a weighted rating for a movie, combining its average rating and number of votes with prior assumptions to avoid bias from low-vote movies. */
    static double weightedRating(Movie m) {
        double votes = m.getNumVotes() == null ? 0 : m.getNumVotes();
        double rating = m.getAvgRating() == null ? PRIOR_MEAN : m.getAvgRating().doubleValue();
        return (votes / (votes + PRIOR_VOTES)) * rating + (PRIOR_VOTES / (votes + PRIOR_VOTES)) * PRIOR_MEAN;
    }

    /** Extracts a set of normalized names from a raw string, splitting by common delimiters and converting to lowercase. */
    private static Set<String> names(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String part : NAME_SPLIT.split(raw.trim())) {
            if (!part.isBlank()) {
                out.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Clamps a value to the range [0, 1]. */
    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private MovieScorer() {
    }
}
