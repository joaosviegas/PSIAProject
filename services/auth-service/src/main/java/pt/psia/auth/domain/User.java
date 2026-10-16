package pt.psia.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a user in the authentication service.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Protected no-arg constructor for JPA. */
    protected User() {}

    /**
     * Constructs a new User with the specified ID, username, and password hash.
     *
     * @param id           the unique identifier for the user
     * @param username     the username of the user
     * @param passwordHash the hashed password of the user
     */
    public User(UUID id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    /** Returns the unique identifier for the user. */
    public UUID getId() { return id; }
    /** Returns the username of the user. */
    public String getUsername() { return username; }
    /** Returns the hashed password of the user. */
    public String getPasswordHash() { return passwordHash; }
    /** Returns the timestamp when the user was created. */
    public Instant getCreatedAt() { return createdAt; }
}
