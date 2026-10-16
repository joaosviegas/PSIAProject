package pt.psia.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A DTO for a login request.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {}
