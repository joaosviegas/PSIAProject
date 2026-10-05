package pt.psia.profile.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pt.psia.profile.domain.Profile;

import java.time.Instant;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    @Query("SELECT DISTINCT p FROM Profile p LEFT JOIN p.favoriteGenres g " +
           "WHERE p.userId <> :callerId " +
           "AND (LOWER(p.displayName) LIKE :pattern OR LOWER(p.username) LIKE :pattern) " +
           "AND (:genre = '' OR g = :genre)")
    Page<Profile> browse(@Param("callerId") UUID callerId,
                         @Param("pattern") String pattern,
                         @Param("genre") String genre,
                         Pageable pageable);

    @Query("SELECT p FROM Profile p WHERE p.updatedAt > :since")
    Page<Profile> findByUpdatedAtAfter(@Param("since") Instant since, Pageable pageable);

    /**
     * Race-safe "create the empty profile if it doesn't exist yet": two parallel
     * requests from a brand-new user can't collide on the primary key.
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO profiles (user_id, username, display_name, updated_at) " +
                   "VALUES (:userId, :username, :displayName, NOW()) ON CONFLICT (user_id) DO NOTHING",
           nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId,
                       @Param("username") String username,
                       @Param("displayName") String displayName);
}
