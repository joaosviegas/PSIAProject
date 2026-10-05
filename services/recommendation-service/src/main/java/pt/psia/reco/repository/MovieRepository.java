package pt.psia.reco.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.psia.reco.domain.Movie;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    long countBy();

    // ponytail: single native query handles all filters via IS NULL bypass; add full-text search if ILIKE gets slow on 100k+ rows
    @Query(value = """
            SELECT * FROM movies m
            WHERE (:query IS NULL OR m.title ILIKE CONCAT('%', :query, '%')
                                  OR m.directors ILIKE CONCAT('%', :query, '%')
                                  OR m.stars ILIKE CONCAT('%', :query, '%'))
              AND (:genre IS NULL OR m.genres ILIKE CONCAT('%', :genre, '%'))
              AND (:yearFrom IS NULL OR m.start_year >= :yearFrom)
              AND (:yearTo IS NULL OR m.start_year <= :yearTo)
              AND (:minRating IS NULL OR m.avg_rating >= :minRating)
            ORDER BY m.num_votes DESC NULLS LAST
            """,
            countQuery = """
            SELECT count(*) FROM movies m
            WHERE (:query IS NULL OR m.title ILIKE CONCAT('%', :query, '%')
                                  OR m.directors ILIKE CONCAT('%', :query, '%')
                                  OR m.stars ILIKE CONCAT('%', :query, '%'))
              AND (:genre IS NULL OR m.genres ILIKE CONCAT('%', :genre, '%'))
              AND (:yearFrom IS NULL OR m.start_year >= :yearFrom)
              AND (:yearTo IS NULL OR m.start_year <= :yearTo)
              AND (:minRating IS NULL OR m.avg_rating >= :minRating)
            """,
            nativeQuery = true)
    Page<Movie> search(@Param("query") String query,
                       @Param("genre") String genre,
                       @Param("yearFrom") Integer yearFrom,
                       @Param("yearTo") Integer yearTo,
                       @Param("minRating") Double minRating,
                       Pageable pageable);

    @Query(value = """
            SELECT * FROM movies m
            WHERE (:genresCsv = '' OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(:genresCsv, ',')) AS g
                WHERE m.genres ILIKE CONCAT('%', g, '%')
            ))
            ORDER BY m.avg_rating DESC NULLS LAST, m.num_votes DESC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<Movie> findCandidates(@Param("genresCsv") String genresCsv, @Param("limit") int limit);

    /**
     * Scoring pool: movies overlapping the given genres (exact tag match, so "Music" doesn't
     * pull in "Musical"), best IMDb-style weighted rating first. {@code genresCsv = ''} means any genre.
     * {@code excludeCsv} is a comma-separated id list ('' = nothing excluded).
     */
    @Query(value = """
            SELECT * FROM movies m
            WHERE (:genresCsv = '' OR string_to_array(m.genres, ';') && string_to_array(:genresCsv, ','))
              AND m.id <> ALL (CAST(string_to_array(:excludeCsv, ',') AS bigint[]))
            ORDER BY (CAST(COALESCE(m.num_votes, 0) AS float8) / (COALESCE(m.num_votes, 0) + 2500)) * COALESCE(m.avg_rating, 0)
                   + (CAST(2500 AS float8) / (COALESCE(m.num_votes, 0) + 2500)) * 6.5 DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Movie> findPool(@Param("genresCsv") String genresCsv,
                         @Param("excludeCsv") String excludeCsv,
                         @Param("limit") int limit);

    /** Random movies from the chosen genres only — the "discover" half of the feed. */
    @Query(value = """
            SELECT * FROM movies m
            WHERE (:genresCsv = '' OR string_to_array(m.genres, ';') && string_to_array(:genresCsv, ','))
              AND m.id <> ALL (CAST(string_to_array(:excludeCsv, ',') AS bigint[]))
              AND COALESCE(m.num_votes, 0) >= :minVotes
              AND COALESCE(m.avg_rating, 0) >= :minRating
            ORDER BY random()
            LIMIT :limit
            """, nativeQuery = true)
    List<Movie> findRandom(@Param("genresCsv") String genresCsv,
                           @Param("excludeCsv") String excludeCsv,
                           @Param("minVotes") int minVotes,
                           @Param("minRating") double minRating,
                           @Param("limit") int limit);
}
