package pt.psia.agent.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.psia.agent.feed.FeedStore;

import java.util.Map;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedStore feedStore;

    public FeedController(FeedStore feedStore) {
        this.feedStore = feedStore;
    }

    @GetMapping
    public ResponseEntity<?> feed(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return feedStore.find(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    feedStore.markPending(userId);
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "pending"));
                });
    }
}
