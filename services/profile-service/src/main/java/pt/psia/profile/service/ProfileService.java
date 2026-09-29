package pt.psia.profile.service;

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

@Service
public class ProfileService {

    private final ProfileRepository repo;

    public ProfileService(ProfileRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return repo.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

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
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileSummary> browse(UUID callerId, String q, String genre, Pageable pageable) {
        String pattern = q != null && !q.isBlank() ? "%" + q.toLowerCase() + "%" : null;
        var page = repo.browse(callerId, pattern, genre, pageable);
        var items = page.getContent().stream().map(this::toSummary).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

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

    private long countShared(Set<?> a, Set<?> b) {
        return a.stream().filter(b::contains).count();
    }

    private ProfileResponse toResponse(Profile p) {
        return new ProfileResponse(p.getUserId().toString(), p.getUsername(), p.getDisplayName(),
                p.getBio(), List.copyOf(p.getFavoriteGenres()), List.copyOf(p.getFavoriteMovieIds()),
                List.copyOf(p.getDislikedMovieIds()), p.getUpdatedAt().toString());
    }

    private ProfileSummary toSummary(Profile p) {
        return new ProfileSummary(p.getUserId().toString(), p.getUsername(), p.getDisplayName(),
                List.copyOf(p.getFavoriteGenres()));
    }

    private Set<String> toSet(List<String> list) {
        return list != null ? new HashSet<>(list) : new HashSet<>();
    }

    private Set<Integer> toIntSet(List<Integer> list) {
        return list != null ? new HashSet<>(list) : new HashSet<>();
    }
}
