package pt.psia.profile.web;

import org.springframework.web.bind.annotation.*;
import pt.psia.profile.service.ProfileService;
import pt.psia.profile.web.dto.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A controller for internal profile operations.
 */
@RestController
@RequestMapping("/internal/profiles")
public class InternalProfileController {

    private final ProfileService profileService;

    /**
     * Creates a new instance of InternalProfileController. 
     * @param profileService the profile service 
     */
    public InternalProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** Returns the profile for the given user ID. */
    @GetMapping("/{userId}")
    public ProfileResponse getProfile(@PathVariable UUID userId) {
        return profileService.getProfile(userId);
    }

    /** Returns a page of profiles updated since the given timestamp, or all profiles if {@code updatedSince} is null. */
    @GetMapping
    public PageResponse<ProfileResponse> findUpdatedSince(
            @RequestParam(required = false) String updatedSince,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Instant since = updatedSince != null ? Instant.parse(updatedSince) : null;
        return profileService.findUpdatedSince(since, page, size);
    }

    /** Returns a list of profiles similar to the given user ID, limited to the specified number of results. */
    @GetMapping("/similar/{userId}")
    public List<ProfileResponse> findSimilar(@PathVariable UUID userId,
                                              @RequestParam(defaultValue = "5") int limit) {
        return profileService.findSimilar(userId, limit);
    }
}
