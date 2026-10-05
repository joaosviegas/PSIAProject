package pt.psia.profile.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pt.psia.profile.service.ProfileService;
import pt.psia.profile.web.dto.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getOrCreate(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"));
    }

    @PutMapping("/me")
    public ProfileResponse upsertMe(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody ProfileRequest request) {
        return profileService.upsert(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                request);
    }

    @GetMapping
    public PageResponse<ProfileSummary> browse(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String genre,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return profileService.browse(UUID.fromString(jwt.getSubject()), q, genre, PageRequest.of(page, size));
    }

    @GetMapping("/{userId}")
    public ProfileResponse getProfile(@PathVariable UUID userId) {
        return profileService.getProfile(userId);
    }
}
