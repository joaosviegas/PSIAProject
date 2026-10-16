package pt.psia.agent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pt.psia.agent.client.dto.MovieDto;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * A client for the recommendation service.
 */
@Component
public class MovieClient {

    private static final ParameterizedTypeReference<List<MovieDto>> MOVIES = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * @param baseUrl the base URL of the recommendation service
     */
    public MovieClient(@Value("${recommendation-service.url}") String baseUrl) {
        // These calls sit on the user's request path, so a hung service must fail fast.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Returns personalized movie recommendations for the given user profile. */
    public List<MovieDto> personalized(Collection<String> genres, Collection<Long> liked, Collection<Long> disliked, int limit) {
        return restClient.get()
                .uri(b -> {
                    b.path("/internal/movies/personalized").queryParam("limit", limit);
                    if (!genres.isEmpty()) b.queryParam("genres", String.join(",", genres));
                    if (!liked.isEmpty()) b.queryParam("liked", join(liked));
                    if (!disliked.isEmpty()) b.queryParam("disliked", join(disliked));
                    return b.build();
                })
                .retrieve()
                .body(MOVIES);
    }

    /** Returns a list of movies that match the given genres, excluding the given movie IDs, up to the given limit. */
    public List<MovieDto> discover(Collection<String> genres, Collection<Long> exclude, int limit) {
        return restClient.get()
                .uri(b -> {
                    b.path("/internal/movies/discover").queryParam("limit", limit);
                    if (!genres.isEmpty()) b.queryParam("genres", String.join(",", genres));
                    if (!exclude.isEmpty()) b.queryParam("exclude", join(exclude));
                    return b.build();
                })
                .retrieve()
                .body(MOVIES);
    }

    /** Returns a list of candidate movies for the given genres, excluding the given movie IDs, up to the given limit. */
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

    /** Returns a list of movies for the given IDs. */
    public List<MovieDto> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return restClient.get()
                .uri(b -> b.path("/internal/movies").queryParam("ids", join(ids)).build())
                .retrieve()
                .body(MOVIES);
    }

    /** Returns a list of movies for the given IMDB IDs. */
    private static String join(Collection<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
