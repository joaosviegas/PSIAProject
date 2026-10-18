package pt.psia.reco.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pt.psia.reco.web.dto.MovieResponse;
import pt.psia.reco.web.dto.RecommendationRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecommendationServiceTest {

    MovieService movieService = mock(MovieService.class);
    LlmClient llmClient = mock(LlmClient.class);
    ProfileClient profileClient = mock(ProfileClient.class);
    RecommendationService service;

    MovieResponse m1 = new MovieResponse(1L, "tt1", "Inception", 2010, List.of("Action", "Sci-Fi"),
            new BigDecimal("8.8"), 2_000_000, "Overview", "http://poster", "Nolan", "DiCaprio", 148);
    MovieResponse m2 = new MovieResponse(2L, "tt2", "Interstellar", 2014, List.of("Sci-Fi", "Drama"),
            new BigDecimal("8.7"), 1_800_000, "Overview", "http://poster", "Nolan", "McConaughey", 169);

    @BeforeEach
    void setUp() {
        service = new RecommendationService(movieService, llmClient, profileClient, new SimpleMeterRegistry());
        when(profileClient.fetchProfile(any())).thenReturn(Optional.empty());

        lenient().when(movieService.candidates(anyList(), anySet(), anyInt())).thenReturn(List.of(m1, m2));
        // The service always uses the keyword-aware overload (keywords may be empty).
        lenient().when(movieService.personalized(anyList(), anyList(), anyList(), anyList(), anyList(), anyInt()))
                .thenReturn(List.of(m1, m2));
        lenient().when(movieService.discover(anyList(), anySet(), anyInt())).thenReturn(List.of(m1, m2));
    }

    @Test
    void quickReturnsHeuristicRankingWithoutLlm() {
        var req = new RecommendationRequest(List.of("Sci-Fi"), "something intense", null);
        var resp = service.quick(req, "Bearer token");

        assertThat(resp.aiGenerated()).isFalse();
        assertThat(resp.movies()).isNotEmpty();
        verifyNoInteractions(llmClient);
    }

    @Test
    void recommendUsesLlmPicksWhenSuccessful() {
        when(llmClient.pickMovies(anyString())).thenReturn(new LlmPicks("Mind-bending picks!", List.of("Inception")));

        var req = new RecommendationRequest(List.of("Sci-Fi"), null, "love Christopher Nolan");
        var resp = service.recommend(req, "Bearer token");

        assertThat(resp.aiGenerated()).isTrue();
        assertThat(resp.summary()).isEqualTo("Mind-bending picks!");
        // The LLM's pick comes first; the rest of the shortlist tops the list up.
        assertThat(resp.movies()).extracting("title").containsExactly("Inception", "Interstellar");
    }

    @Test
    void llmPicksAreMatchedLeniently() {
        when(llmClient.pickMovies(anyString()))
                .thenReturn(new LlmPicks("Space!", List.of("2. \"interstellar\" (2014)")));

        var resp = service.recommend(new RecommendationRequest(List.of("Sci-Fi"), null, null), "Bearer token");

        assertThat(resp.aiGenerated()).isTrue();
        assertThat(resp.movies()).extracting("title").containsExactly("Interstellar", "Inception");
    }

    @Test
    void recommendFallsBackToHeuristicWhenLlmFails() {
        when(llmClient.pickMovies(anyString())).thenThrow(new RuntimeException("LLM timeout"));

        var req = new RecommendationRequest(List.of("Sci-Fi"), null, null);
        var resp = service.recommend(req, "Bearer token");

        assertThat(resp.aiGenerated()).isFalse();
        assertThat(resp.movies()).extracting("title").contains("Inception", "Interstellar");
    }

    @Test
    void recommendFallsBackWhenNoPickMatchesACandidate() {
        when(llmClient.pickMovies(anyString())).thenReturn(new LlmPicks("Hmm", List.of("Not in the list")));

        var resp = service.recommend(new RecommendationRequest(List.of("Sci-Fi"), null, null), "Bearer token");

        assertThat(resp.aiGenerated()).isFalse();
        assertThat(resp.movies()).extracting("title").containsExactly("Inception", "Interstellar");
    }

    @Test
    void notesBecomeKeywordsForTheShortlist() {
        service.quick(new RecommendationRequest(List.of("War"), null, "something with tanks"), "Bearer token");

        verify(movieService).personalized(anyList(), anyList(), anyList(), anyList(), eq(List.of("tank")), anyInt());
    }

    @Test
    void promptIncludesTheUsersNotesAndTheCandidates() {
        when(llmClient.pickMovies(anyString())).thenReturn(new LlmPicks("Space!", List.of("Inception")));

        service.recommend(new RecommendationRequest(List.of("Sci-Fi"), null, "tanks"), "Bearer token");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).pickMovies(prompt.capture());
        assertThat(prompt.getValue()).contains("tanks", "Inception", "Interstellar");
    }
}