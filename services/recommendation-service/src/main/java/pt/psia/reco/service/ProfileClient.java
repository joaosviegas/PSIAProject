package pt.psia.reco.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ProfileClient {

    private static final Logger log = LoggerFactory.getLogger(ProfileClient.class);

    private final RestClient restClient;

    public ProfileClient(@Value("${profile.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Optional<String> fetchInterestsSummary(String bearerToken) {
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

            StringBuilder sb = new StringBuilder();

            Object genresObj = profile.getOrDefault("favoriteGenres", profile.get("genres"));
            if (genresObj instanceof List<?> list) {
                list.forEach(g -> sb.append(g).append(", "));
            }

            Object bioObj = profile.getOrDefault("bio", profile.get("interests"));
            if (bioObj != null) {
                sb.append(bioObj);
            }

            String summary = sb.toString().trim();
            return summary.isBlank() ? Optional.empty() : Optional.of(summary);
        } catch (Exception e) {
            log.info("Could not fetch profile for prompt enrichment (continuing without it): {}", e.getMessage());
            return Optional.empty();
        }
    }
}