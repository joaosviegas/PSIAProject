package pt.psia.agent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import pt.psia.agent.client.dto.PageDto;
import pt.psia.agent.client.dto.ProfileDto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProfileClient {

    private static final int PAGE_SIZE = 50;

    private final RestClient restClient;

    public ProfileClient(@Value("${profile-service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Empty (instead of an exception) when the user has no profile yet. */
    public Optional<ProfileDto> find(UUID userId) {
        try {
            return Optional.ofNullable(get(userId));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public List<ProfileDto> updatedSince(Instant since) {
        List<ProfileDto> all = new ArrayList<>();
        int page = 0;
        PageDto<ProfileDto> result;
        do {
            int current = page;
            result = restClient.get()
                    .uri(b -> {
                        b.path("/internal/profiles").queryParam("page", current).queryParam("size", PAGE_SIZE);
                        if (since != null) b.queryParam("updatedSince", since.toString());
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<PageDto<ProfileDto>>() {});
            all.addAll(result.items());
            page++;
        } while (page < result.totalPages());
        return all;
    }

    public ProfileDto get(UUID userId) {
        return restClient.get().uri("/internal/profiles/{id}", userId).retrieve().body(ProfileDto.class);
    }

    public List<ProfileDto> similar(UUID userId, int limit) {
        return restClient.get()
                .uri(b -> b.path("/internal/profiles/similar/{id}").queryParam("limit", limit).build(userId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ProfileDto>>() {});
    }
}
