package pt.psia.reco.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/api/movies/ping")
    public String ping() {
        return "recommendation-service ok";
    }
}
