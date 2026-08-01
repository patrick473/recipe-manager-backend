package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /auth/login}. No {@code @Size} on
 * {@code password} here — a login attempt only needs a password to be
 * "present," the minimum-length strength check only matters when a password
 * is being set (see {@link RegisterRequest}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LoginRequest", description = "Payload for logging into an existing account")
public class LoginRequest {

    @NotBlank(message = "Username must not be blank")
    @Schema(description = "Account username", example = "jsmith", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Schema(description = "Account password", example = "correcthorsebattery", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
