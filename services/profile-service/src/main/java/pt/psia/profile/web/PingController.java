package pt.psia.profile.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/api/profiles/ping")
    public String ping() {
        return "profile-service ok";
    }
}
