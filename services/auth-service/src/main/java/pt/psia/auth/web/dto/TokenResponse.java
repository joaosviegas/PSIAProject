package pt.psia.auth.web.dto;

/**
 * A DTO for a token response.
 */
public record TokenResponse(String token, String tokenType, long expiresIn, String userId, String username) {}
