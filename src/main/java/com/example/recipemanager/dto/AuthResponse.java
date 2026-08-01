package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body returned by both {@code POST /auth/register} and
 * {@code POST /auth/login}. The frontend never needs to decode the JWT
 * itself — the identity it needs comes back in plain JSON alongside the token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthResponse", description = "Issued on successful register/login")
public class AuthResponse {

    @Schema(description = "Signed JWT to send as `Authorization: Bearer <token>` on subsequent requests")
    private String token;

    @Schema(description = "Authenticated account id", example = "1")
    private Long userId;

    @Schema(description = "Authenticated account username", example = "jsmith")
    private String username;
}
