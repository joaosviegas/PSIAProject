package pt.psia.reco.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pt.psia.reco.domain.Movie;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovieScorerTest {

    private static Movie movie(String title, int year, String genres, String rating, int votes) {
        return new Movie("tt" + title.hashCode(), title, year, genres, new BigDecimal(rating), votes);
    }

    private static MovieScorer.Taste taste(List<String> primary, List<String> secondary, List<Movie> liked, List<Movie> disliked) {
        return MovieScorer.taste(primary, secondary, liked, disliked);
    }

    @Test
    void pickedGenreOutranksOtherwiseBetterRatedMovieFromAnotherGenre() {
        Movie scifi = movie("Arrival", 2016, "Drama;Sci-Fi", "7.9", 700_000);
        Movie western = movie("Unforgiven", 1992, "Western", "8.2", 450_000);

        var ranked = MovieScorer.rank(List.of(western, scifi), taste(List.of("Sci-Fi"), List.of(), List.of(), List.of()), 2);

        assertThat(ranked).extracting(Movie::getTitle).containsExactly("Arrival", "Unforgiven");
    }

    @Test
    void tinyVoteCountsDoNotBeatWellKnownFilms() {
        Movie obscure = movie("Obscure", 2010, "Drama", "9.9", 12);
        Movie known = movie("Known", 2010, "Drama", "8.4", 900_000);

        var ranked = MovieScorer.rank(List.of(obscure, known), taste(List.of("Drama"), List.of(), List.of(), List.of()), 2);

        assertThat(ranked.get(0).getTitle()).isEqualTo("Known");
    }

    @Test
    void genresOfDislikedMoviesArePenalised() {
        Movie horror = movie("Scary", 2015, "Horror", "7.5", 300_000);
        Movie comedy = movie("Funny", 2015, "Comedy", "7.5", 300_000);
        Movie dislikedHorror = movie("Disliked", 2014, "Horror", "5.0", 100_000);

        var ranked = MovieScorer.rank(List.of(horror, comedy),
                taste(List.of(), List.of(), List.of(), List.of(dislikedHorror)), 2);

        assertThat(ranked.get(0).getTitle()).isEqualTo("Funny");
    }

    @Test
    void likedMoviesPullTheirGenresUp() {
        Movie war = movie("War Movie", 2000, "War;Drama", "7.5", 300_000);
        Movie romance = movie("Love Story", 2000, "Romance", "7.5", 300_000);
        Movie likedWar = movie("Saving", 1998, "War;Drama", "8.6", 1_400_000);

        var ranked = MovieScorer.rank(List.of(romance, war),
                taste(List.of(), List.of(), List.of(likedWar), List.of()), 2);

        assertThat(ranked.get(0).getTitle()).isEqualTo("War Movie");
    }

    @Test
    void sharedDirectorGivesABonus() {
        Movie byFavourite = movie("Another Nolan", 2012, "Action", "7.5", 300_000);
        Movie byStranger = movie("Someone Else", 2012, "Action", "7.5", 300_000);
        ReflectionTestUtils.setField(byFavourite, "directors", "Christopher Nolan");
        ReflectionTestUtils.setField(byStranger, "directors", "Somebody Else");
        Movie liked = movie("Inception", 2010, "Action", "8.8", 2_400_000);
        ReflectionTestUtils.setField(liked, "directors", "Christopher Nolan");

        var ranked = MovieScorer.rank(List.of(byStranger, byFavourite),
                taste(List.of(), List.of(), List.of(liked), List.of()), 2);

        assertThat(ranked.get(0).getTitle()).isEqualTo("Another Nolan");
    }

    @Test
    void topGenresCountsFrequencyThenName() {
        var movies = List.of(
                movie("A", 2000, "Drama;Crime", "7.0", 1000),
                movie("B", 2001, "Drama;Comedy", "7.0", 1000),
                movie("C", 2002, "Crime", "7.0", 1000));

        assertThat(MovieScorer.topGenres(movies, 2)).containsExactly("Crime", "Drama");
    }
}
