package pt.psia.profile.web;

import org.springframework.web.bind.annotation.*;
import pt.psia.profile.service.ProfileService;
import pt.psia.profile.web.dto.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/profiles")
public class InternalProfileController {

    private final ProfileService profileService;

    public InternalProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{userId}")
    public ProfileResponse getProfile(@PathVariable UUID userId) {
        return profileService.getProfile(userId);
    }

    @GetMapping
    public PageResponse<ProfileResponse> findUpdatedSince(
            @RequestParam(required = false) String updatedSince,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Instant since = updatedSince != null ? Instant.parse(updatedSince) : null;
        return profileService.findUpdatedSince(since, page, size);
    }

    @GetMapping("/similar/{userId}")
    public List<ProfileResponse> findSimilar(@PathVariable UUID userId,
                                              @RequestParam(defaultValue = "5") int limit) {
        return profileService.findSimilar(userId, limit);
    }
}
