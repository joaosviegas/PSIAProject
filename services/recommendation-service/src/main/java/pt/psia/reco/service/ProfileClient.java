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

@Component
public class ProfileClient {

    private static final Logger log = LoggerFactory.getLogger(ProfileClient.class);

    /** What the recommender needs to know about the caller. */
    public record ProfileSnapshot(List<String> genres, String bio, List<Long> liked, List<Long> disliked) {
    }

    private final RestClient restClient;

    public ProfileClient(@Value("${profile.base-url}") String baseUrl) {
        // Short timeouts: the profile only enriches the request, it must never hold it up.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

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

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private static List<Long> longs(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Number.class::isInstance).map(n -> ((Number) n).longValue()).toList();
        }
        return List.of();
    }
}
