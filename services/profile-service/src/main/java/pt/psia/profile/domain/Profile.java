package pt.psia.profile.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/**
 * Represents a user profile in the system.
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(length = 500)
    private String bio;

    @ElementCollection
    @CollectionTable(name = "profile_favorite_genres", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "genre")
    private Set<String> favoriteGenres = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "profile_favorite_movie_ids", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "movie_id")
    private Set<Integer> favoriteMovieIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "profile_disliked_movie_ids", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "movie_id")
    private Set<Integer> dislikedMovieIds = new HashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Protected no-arg constructor for JPA. */
    protected Profile() {}

    /**
     * Constructs a new Profile
     * @param userId the unique identifier for the user
     * @param username the username of the user
     * @param displayName the display name of the user
     * @param bio the biography of the user
     * @param favoriteGenres the user's favorite genres
     * @param favoriteMovieIds the user's favorite movie IDs
     * @param dislikedMovieIds the user's disliked movie IDs
     */
    public Profile(UUID userId, String username, String displayName, String bio,
                   Set<String> favoriteGenres, Set<Integer> favoriteMovieIds,
                   Set<Integer> dislikedMovieIds) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.bio = bio;
        this.favoriteGenres = favoriteGenres != null ? favoriteGenres : new HashSet<>();
        this.favoriteMovieIds = favoriteMovieIds != null ? favoriteMovieIds : new HashSet<>();
        this.dislikedMovieIds = dislikedMovieIds != null ? dislikedMovieIds : new HashSet<>();
        this.updatedAt = Instant.now();
    }

    /** Gets the unique identifier for the user. */
    public UUID getUserId() { return userId; }
    /** Gets the username of the user. */
    public String getUsername() { return username; }
    /** Gets the display name of the user. */
    public String getDisplayName() { return displayName; }
    /** Sets the display name of the user. */
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    /** Gets the biography of the user. */
    public String getBio() { return bio; }
    /** Sets the biography of the user. */
    public void setBio(String bio) { this.bio = bio; }
    /** Gets the user's favorite genres. */
    public Set<String> getFavoriteGenres() { return favoriteGenres; }
    /** Sets the user's favorite genres. */
    public void setFavoriteGenres(Set<String> favoriteGenres) { this.favoriteGenres = favoriteGenres; }
    /** Gets the user's favorite movie IDs. */
    public Set<Integer> getFavoriteMovieIds() { return favoriteMovieIds; }
    /** Sets the user's favorite movie IDs. */
    public void setFavoriteMovieIds(Set<Integer> favoriteMovieIds) { this.favoriteMovieIds = favoriteMovieIds; }
    /** Gets the user's disliked movie IDs. */
    public Set<Integer> getDislikedMovieIds() { return dislikedMovieIds; }
    /** Sets the user's disliked movie IDs. */
    public void setDislikedMovieIds(Set<Integer> dislikedMovieIds) { this.dislikedMovieIds = dislikedMovieIds; }
    /** Gets the timestamp when the profile was last updated. */
    public Instant getUpdatedAt() { return updatedAt; }
    /** Sets the timestamp when the profile was last updated. */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
