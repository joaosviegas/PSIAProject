package pt.psia.reco.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client for fetching user profile information from the profile service.
 */
@Component
public class ProfileClient {

    /** Logger for logging profile client activities. */
    private static final Logger log = LoggerFactory.getLogger(ProfileClient.class);

    /** What the recommender needs to know about the caller's profile. */
    public record ProfileSnapshot(List<String> genres, String bio, List<Long> liked, List<Long> disliked) {
    }

    /** The RestClient used to make HTTP requests to the profile service. */
    private final RestClient restClient;

    /**
     * Constructs a new ProfileClient with the given base URL for the profile service.
     * Short timeouts are set to ensure that the profile service does not hold up requests.
     *
     * @param baseUrl the base URL of the profile service
     */
    public ProfileClient(@Value("${profile.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Fetches the user's profile information using the provided bearer token. */
    public Optional<ProfileSnapshot> fetchProfile(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = restClient.get()
                    .uri("/api/profiles/me")
                    .header("Authorization", bearerToken)
                    .retrieve()
                    .body(Map.class);

            if (profile == null) {
                return Optional.empty();
            }

            String bio = profile.get("bio") instanceof String s ? s : null;
            return Optional.of(new ProfileSnapshot(
                    strings(profile.get("favoriteGenres")),
                    bio,
                    longs(profile.get("favoriteMovieIds")),
                    longs(profile.get("dislikedMovieIds"))));
        } catch (Exception e) {
            log.info("Could not fetch profile (continuing without it): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Converts an object to a list of strings if possible, otherwise returns an empty list. */
    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    /** Converts an object to a list of longs if possible, otherwise returns an empty list. */
    private static List<Long> longs(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Number.class::isInstance).map(n -> ((Number) n).longValue()).toList();
        }
        return List.of();
    }
}
