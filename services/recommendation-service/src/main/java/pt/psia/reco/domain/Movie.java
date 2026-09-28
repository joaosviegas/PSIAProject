package pt.psia.reco.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

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

    /** Semicolon-separated, e.g. "Action;Sci-Fi". Use {@link #getGenreList()}. */
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

    protected Movie() {
        // JPA
    }

    public Movie(String imdbId, String title, Integer startYear, String genres,
                 BigDecimal avgRating, Integer numVotes) {
        this.imdbId = imdbId;
        this.title = title;
        this.startYear = startYear;
        this.genres = genres;
        this.avgRating = avgRating;
        this.numVotes = numVotes;
    }

    public List<String> getGenreList() {
        if (genres == null || genres.isBlank()) {
            return List.of();
        }
        return Arrays.asList(genres.split(";"));
    }

    public Long getId() { return id; }
    public String getImdbId() { return imdbId; }
    public String getTitle() { return title; }
    public Integer getStartYear() { return startYear; }
    public String getGenres() { return genres; }
    public BigDecimal getAvgRating() { return avgRating; }
    public Integer getNumVotes() { return numVotes; }
    public String getOverview() { return overview; }
    public String getPosterUrl() { return posterUrl; }
    public String getDirectors() { return directors; }
    public String getStars() { return stars; }
    public Integer getRuntimeMinutes() { return runtimeMinutes; }
}
