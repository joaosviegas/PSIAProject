package pt.psia.profile.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.psia.profile.domain.Profile;
import pt.psia.profile.repository.ProfileRepository;
import pt.psia.profile.web.dto.*;

import java.time.Instant;
import java.util.*;

/**
 * Service class for managing user profiles.
 */
@Service
public class ProfileService {

    private final ProfileRepository repo;
    private final Counter updatesCounter;

    /**
     * Constructs a new ProfileService with the given repository and meter registry.
     *
     * @param repo the profile repository
     * @param registry the meter registry for metrics
     */
    public ProfileService(ProfileRepository repo, MeterRegistry registry) {
        this.repo = repo;
        this.updatesCounter = Counter.builder("profile_updates_total").register(registry);
    }

    /** Returns the profile for the given user ID. */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return repo.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    /**
     * Returns the caller's profile, creating an empty one (display name = username)
     * the first time it is asked for. This is what makes every registered user show
     * up in People and gives the feed agent something to read for a brand-new account.
     */
    @Transactional
    public ProfileResponse getOrCreate(UUID userId, String username) {
        String name = (username == null || username.isBlank()) ? userId.toString().substring(0, 8) : username;
        repo.insertIfAbsent(userId, truncate(name, 30), truncate(name, 50));
        return repo.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    /** Truncates a string to the specified maximum length. */
    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Updates or creates a profile for the given user ID. */
    @Transactional
    public ProfileResponse upsert(UUID userId, String username, ProfileRequest req) {
        var profile = repo.findById(userId).orElse(null);
        if (profile == null) {
            profile = new Profile(userId, username, req.displayName(), req.bio(),
                    toSet(req.favoriteGenres()), toIntSet(req.favoriteMovieIds()), toIntSet(req.dislikedMovieIds()));
        } else {
            profile.setDisplayName(req.displayName());
            profile.setBio(req.bio());
            profile.getFavoriteGenres().clear();
            if (req.favoriteGenres() != null) profile.getFavoriteGenres().addAll(req.favoriteGenres());
            profile.getFavoriteMovieIds().clear();
            if (req.favoriteMovieIds() != null) profile.getFavoriteMovieIds().addAll(req.favoriteMovieIds());
            profile.getDislikedMovieIds().clear();
            if (req.dislikedMovieIds() != null) profile.getDislikedMovieIds().addAll(req.dislikedMovieIds());
            profile.setUpdatedAt(Instant.now());
        }
        repo.save(profile);
        updatesCounter.increment();
        return toResponse(profile);
    }

    /** Browses profiles based on the given criteria. */
    @Transactional(readOnly = true)
    public PageResponse<ProfileSummary> browse(UUID callerId, String q, String genre, Pageable pageable) {
        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        String genreFilter = genre == null ? "" : genre.trim();
        var page = repo.browse(callerId, pattern, genreFilter, pageable);
        var items = page.getContent().stream().map(this::toSummary).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Finds profiles updated since the given timestamp, with pagination. */
    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> findUpdatedSince(Instant since, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("updatedAt"));
        Page<Profile> result = since != null
                ? repo.findByUpdatedAtAfter(since, pageable)
                : repo.findAll(pageable);
        var items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** Finds profiles similar to the given user ID, up to the specified limit. */
    @Transactional(readOnly = true)
    public List<ProfileResponse> findSimilar(UUID userId, int limit) {
        var profile = repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        var myGenres = profile.getFavoriteGenres();
        var myMovies = profile.getFavoriteMovieIds();
        return repo.findAll().stream()
                .filter(p -> !p.getUserId().equals(userId))
                .filter(p -> countShared(p.getFavoriteGenres(), myGenres) > 0
                          || countShared(p.getFavoriteMovieIds(), myMovies) > 0)
                .sorted(Comparator
                        .comparingLong((Profile p) -> -countShared(p.getFavoriteGenres(), myGenres))
                        .thenComparingLong(p -> -countShared(p.getFavoriteMovieIds(), myMovies)))
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    /** Counts the number of shared elements between two sets. */
    private long countShared(Set<?> a, Set<?> b) {
        return a.stream().filter(b::contains).count();
    }

    /** Converts a Profile entity to a ProfileResponse DTO. */
    private ProfileResponse toResponse(Profile p) {
        return new ProfileResponse(p.getUserId().toString(), p.getUsername(), p.getDisplayName(),
                p.getBio(), List.copyOf(p.getFavoriteGenres()), List.copyOf(p.getFavoriteMovieIds()),
                List.copyOf(p.getDislikedMovieIds()), p.getUpdatedAt().toString());
    }

    /** Converts a Profile entity to a ProfileSummary DTO. */
    private ProfileSummary toSummary(Profile p) {
        return new ProfileSummary(p.getUserId().toString(), p.getUsername(), p.getDisplayName(),
                List.copyOf(p.getFavoriteGenres()));
    }

    /** Converts a list to a set, returning an empty set if the list is null. */
    private Set<String> toSet(List<String> list) {
        return list != null ? new HashSet<>(list) : new HashSet<>();
    }

    /** Converts a list of integers to a set, returning an empty set if the list is null. */
    private Set<Integer> toIntSet(List<Integer> list) {
        return list != null ? new HashSet<>(list) : new HashSet<>();
    }
}
