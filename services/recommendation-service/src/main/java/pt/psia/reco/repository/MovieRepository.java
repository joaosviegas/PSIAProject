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
}
