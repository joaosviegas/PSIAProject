package pt.psia.auth.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A simple controller to check the health of the auth-service.
 */
@RestController
public class PingController {

    @GetMapping("/api/auth/ping")
    public String ping() {
        return "auth-service ok";
    }
}
