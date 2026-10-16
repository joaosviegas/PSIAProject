package pt.psia.profile.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A simple controller to check if the profile service is running.
 */
@RestController
public class PingController {

    @GetMapping("/api/profiles/ping")
    public String ping() {
        return "profile-service ok";
    }
}
