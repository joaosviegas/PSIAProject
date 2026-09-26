package pt.psia.agent.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/api/feed/ping")
    public String ping() {
        return "agent-service ok";
    }
}
