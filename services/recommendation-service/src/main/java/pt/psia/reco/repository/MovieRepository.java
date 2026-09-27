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

    /**
     * Free-text title search plus an optional genre filter (substring match
     * against the semicolon-separated genres column). Either filter can be
     * null to be ignored.
     */
    @Query(value = """
            SELECT * FROM movies m
            WHERE (:title IS NULL OR m.title ILIKE CONCAT('%', :title, '%'))
              AND (:genre IS NULL OR m.genres ILIKE CONCAT('%', :genre, '%'))
            ORDER BY m.num_votes DESC NULLS LAST
            """,
            countQuery = """
            SELECT count(*) FROM movies m
            WHERE (:title IS NULL OR m.title ILIKE CONCAT('%', :title, '%'))
              AND (:genre IS NULL OR m.genres ILIKE CONCAT('%', :genre, '%'))
            """,
            nativeQuery = true)
    Page<Movie> search(@Param("title") String title, @Param("genre") String genre, Pageable pageable);

    /**
     * Candidate pool for the LLM: movies matching at least one of the
     * requested genres, best-rated first. Used by RecommendationService.
     */
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
