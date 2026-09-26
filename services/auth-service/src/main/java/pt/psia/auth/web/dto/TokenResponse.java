package pt.psia.auth.web.dto;

public record TokenResponse(String token, String tokenType, long expiresIn, String userId, String username) {}
