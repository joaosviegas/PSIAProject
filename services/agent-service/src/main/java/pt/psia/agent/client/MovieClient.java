package pt.psia.agent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pt.psia.agent.client.dto.MovieDto;

import java.util.Collection;
import java.util.List;

@Component
public class MovieClient {

    private static final ParameterizedTypeReference<List<MovieDto>> MOVIES = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public MovieClient(@Value("${recommendation-service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<MovieDto> candidates(Collection<String> genres, Collection<Long> exclude, int limit) {
        return restClient.get()
                .uri(b -> {
                    b.path("/internal/movies/candidates").queryParam("limit", limit);
                    if (!genres.isEmpty()) b.queryParam("genres", String.join(",", genres));
                    if (!exclude.isEmpty()) b.queryParam("exclude", join(exclude));
                    return b.build();
                })
                .retrieve()
                .body(MOVIES);
    }

    public List<MovieDto> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return restClient.get()
                .uri(b -> b.path("/internal/movies").queryParam("ids", join(ids)).build())
                .retrieve()
                .body(MOVIES);
    }

    private static String join(Collection<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
