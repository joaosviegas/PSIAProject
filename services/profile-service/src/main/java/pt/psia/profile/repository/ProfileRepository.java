package pt.psia.profile.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.psia.profile.domain.Profile;

import java.time.Instant;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    @Query("SELECT DISTINCT p FROM Profile p LEFT JOIN p.favoriteGenres g " +
           "WHERE p.userId != :callerId " +
           "AND (:pattern IS NULL OR LOWER(p.displayName) LIKE :pattern " +
           "     OR LOWER(p.username) LIKE :pattern) " +
           "AND (:genre IS NULL OR g = :genre)")
    Page<Profile> browse(@Param("callerId") UUID callerId,
                         @Param("pattern") String pattern,
                         @Param("genre") String genre,
                         Pageable pageable);

    @Query("SELECT p FROM Profile p WHERE p.updatedAt > :since")
    Page<Profile> findByUpdatedAtAfter(@Param("since") Instant since, Pageable pageable);
}
