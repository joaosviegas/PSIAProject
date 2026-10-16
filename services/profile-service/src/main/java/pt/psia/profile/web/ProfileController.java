package pt.psia.profile.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pt.psia.profile.service.ProfileService;
import pt.psia.profile.web.dto.*;

import java.util.UUID;

/**
 * A controller for profile operations.
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    /** 
     * Creates a new instance of ProfileController.
     * @param profileService the profile service 
    */
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** Returns the profile of the currently authenticated user, creating it if it doesn't exist. */
    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getOrCreate(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"));
    }

    /** Updates or creates the profile of the currently authenticated user. */
    @PutMapping("/me")
    public ProfileResponse upsertMe(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody ProfileRequest request) {
        return profileService.upsert(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                request);
    }

    /** Returns a page of profiles matching the given query and genre, or all profiles if no query is provided. */
    @GetMapping
    public PageResponse<ProfileSummary> browse(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String genre,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return profileService.browse(UUID.fromString(jwt.getSubject()), q, genre, PageRequest.of(page, size));
    }

    /** Returns the profile for the given user ID. */
    @GetMapping("/{userId}")
    public ProfileResponse getProfile(@PathVariable UUID userId) {
        return profileService.getProfile(userId);
    }
}
