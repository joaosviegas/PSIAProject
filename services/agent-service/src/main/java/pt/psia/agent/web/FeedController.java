package pt.psia.agent.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.psia.agent.client.dto.MovieDto;
import pt.psia.agent.feed.FeedResponse;
import pt.psia.agent.feed.FeedService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /** Always answers immediately; see {@link FeedResponse} for the three states. */
    @GetMapping
    public FeedResponse feed(@AuthenticationPrincipal Jwt jwt) {
        return feedService.get(jwt.getSubject());
    }

    /** Start rebuilding the AI feed now (call after saving preferences); returns without waiting. */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@AuthenticationPrincipal Jwt jwt) {
        feedService.refresh(jwt.getSubject());
        return ResponseEntity.accepted().body(Map.of("status", "building"));
    }

    /** A fresh random batch from the user's categories. */
    @GetMapping("/discover")
    public List<MovieDto> discover(@AuthenticationPrincipal Jwt jwt) {
        return feedService.discover(jwt.getSubject());
    }
}
