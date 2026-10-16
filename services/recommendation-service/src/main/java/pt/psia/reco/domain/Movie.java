package pt.psia.reco.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a movie entity with various attributes such as IMDb ID, title, genres, ratings, and more.
 */
@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imdb_id", nullable = false, unique = true)
    private String imdbId;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_year")
    private Integer startYear;

    @Column
    private String genres;

    @Column(name = "avg_rating")
    private BigDecimal avgRating;

    @Column(name = "num_votes")
    private Integer numVotes;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Column(name = "poster_url")
    private String posterUrl;

    @Column
    private String directors;

    @Column
    private String stars;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    /** Protected no-args constructor for JPA. */
    protected Movie() {
        // JPA
    }

    /**
     * Constructs a new Movie instance with the specified attributes.
     * @param imdbId The IMDb ID of the movie.
     * @param title The title of the movie.
     * @param startYear The release year of the movie.
     * @param genres Semicolon-separated list of genres associated with the movie.
     * @param avgRating The average rating of the movie.
     * @param numVotes The number of votes the movie has received.
     */
    public Movie(String imdbId, String title, Integer startYear, String genres,
                 BigDecimal avgRating, Integer numVotes) {
        this.imdbId = imdbId;
        this.title = title;
        this.startYear = startYear;
        this.genres = genres;
        this.avgRating = avgRating;
        this.numVotes = numVotes;
    }

    /** Returns the list of genres associated with the movie. */
    public List<String> getGenreList() {
        if (genres == null || genres.isBlank()) {
            return List.of();
        }
        return Arrays.asList(genres.split(";"));
    }

    /** Returns the ID of the movie. */
    public Long getId() { return id; }
    /** Returns the IMDb ID of the movie. */
    public String getImdbId() { return imdbId; }
    /** Returns the title of the movie. */
    public String getTitle() { return title; }
    /** Returns the release year of the movie. */
    public Integer getStartYear() { return startYear; }
    /** Returns the semicolon-separated list of genres associated with the movie. */
    public String getGenres() { return genres; }
    /** Returns the average rating of the movie. */
    public BigDecimal getAvgRating() { return avgRating; }
    /** Returns the number of votes the movie has received. */
    public Integer getNumVotes() { return numVotes; }
    /** Returns the overview of the movie. */
    public String getOverview() { return overview; }
    /** Returns the URL of the movie's poster. */
    public String getPosterUrl() { return posterUrl; }
    /** Returns the directors of the movie. */
    public String getDirectors() { return directors; }
    /** Returns the stars of the movie. */
    public String getStars() { return stars; }
    /** Returns the runtime of the movie in minutes. */
    public Integer getRuntimeMinutes() { return runtimeMinutes; }
}
