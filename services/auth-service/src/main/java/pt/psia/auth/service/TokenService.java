package pt.psia.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import pt.psia.auth.domain.User;

import java.time.Instant;

/**
 * Service for generating JWT tokens for authenticated users.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final long ttlMinutes;

    /**
     * Constructs a new TokenService with the specified JWT encoder and token time-to-live.
     *
     * @param encoder    the JWT encoder
     * @param ttlMinutes the time-to-live for tokens in minutes
     */
    public TokenService(JwtEncoder encoder, @Value("${jwt.ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder;
        this.ttlMinutes = ttlMinutes;
    }

    /** Generates a JWT token for the specified user. */
    public String generateToken(User user) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlMinutes * 60))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Returns the time-to-live for tokens in seconds. */
    public long getTtlSeconds() {
        return ttlMinutes * 60;
    }
}
