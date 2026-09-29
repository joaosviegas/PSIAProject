package pt.psia.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pt.psia.profile.domain.Profile;
import pt.psia.profile.repository.ProfileRepository;
import pt.psia.profile.service.ProfileService;
import pt.psia.profile.web.dto.ProfileRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileServiceTest {

    private ProfileRepository repo;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProfileRepository.class);
        service = new ProfileService(repo);
    }

    @Test
    void upsertCreatesNewProfile() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new ProfileRequest("Alice", "loves sci-fi", List.of("Sci-Fi"), List.of(1, 2), List.of(3));
        var userId = UUID.randomUUID();
        var resp = service.upsert(userId, "alice", req);

        assertEquals(userId.toString(), resp.userId());
        assertEquals("alice", resp.username());
        assertEquals("Alice", resp.displayName());
        assertEquals("loves sci-fi", resp.bio());
        assertTrue(resp.favoriteGenres().contains("Sci-Fi"));
        assertTrue(resp.favoriteMovieIds().containsAll(List.of(1, 2)));
        assertTrue(resp.dislikedMovieIds().contains(3));
        verify(repo).save(any());
    }

    @Test
    void upsertUpdatesExistingProfile() {
        var userId = UUID.randomUUID();
        var existing = new Profile(userId, "alice", "Old Name", "old bio",
                new HashSet<>(Set.of("Drama")), new HashSet<>(Set.of(10)), new HashSet<>(Set.of(20)));
        when(repo.findById(userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new ProfileRequest("New Name", "new bio", List.of("Sci-Fi", "Action"), List.of(1), List.of());
        var resp = service.upsert(userId, "alice", req);

        assertEquals("New Name", resp.displayName());
        assertEquals("new bio", resp.bio());
        assertEquals(2, resp.favoriteGenres().size());
        assertEquals(List.of(1), resp.favoriteMovieIds());
        assertTrue(resp.dislikedMovieIds().isEmpty());
    }

    @Test
    void getProfileNotFound() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.getProfile(UUID.randomUUID()));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void findSimilarRankedBySharedGenresThenMovies() {
        var target = UUID.randomUUID();
        var u1 = UUID.randomUUID();
        var u2 = UUID.randomUUID();
        var u3 = UUID.randomUUID();

        var targetProfile = new Profile(target, "target", "Target", null,
                Set.of("Sci-Fi", "Drama", "Action"), Set.of(1, 2, 3), Set.of());
        var profile1 = new Profile(u1, "user1", "User 1", null,
                Set.of("Sci-Fi"), Set.of(1), Set.of());           // 1 genre, 1 movie
        var profile2 = new Profile(u2, "user2", "User 2", null,
                Set.of("Sci-Fi", "Drama"), Set.of(), Set.of());    // 2 genres, 0 movies
        var profile3 = new Profile(u3, "user3", "User 3", null,
                Set.of("Sci-Fi", "Drama"), Set.of(1, 2), Set.of()); // 2 genres, 2 movies

        when(repo.findById(target)).thenReturn(Optional.of(targetProfile));
        when(repo.findAll()).thenReturn(List.of(targetProfile, profile1, profile2, profile3));

        var result = service.findSimilar(target, 5);

        assertEquals(3, result.size());
        // profile3 first: 2 shared genres + 2 shared movies
        assertEquals(u3.toString(), result.get(0).userId());
        // profile2 second: 2 shared genres + 0 shared movies
        assertEquals(u2.toString(), result.get(1).userId());
        // profile1 third: 1 shared genre + 1 shared movie
        assertEquals(u1.toString(), result.get(2).userId());
    }

    @Test
    void findSimilarExcludesUsersWithNoSharedTaste() {
        var target = UUID.randomUUID();
        var stranger = UUID.randomUUID();

        var targetProfile = new Profile(target, "target", "Target", null,
                Set.of("Sci-Fi"), Set.of(1), Set.of());
        var strangerProfile = new Profile(stranger, "stranger", "Stranger", null,
                Set.of("Comedy"), Set.of(99), Set.of());

        when(repo.findById(target)).thenReturn(Optional.of(targetProfile));
        when(repo.findAll()).thenReturn(List.of(targetProfile, strangerProfile));

        var result = service.findSimilar(target, 5);
        assertTrue(result.isEmpty());
    }
}
