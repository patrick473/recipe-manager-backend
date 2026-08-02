package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /auth/register}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RegisterRequest", description = "Payload for creating a new account")
public class RegisterRequest {

    @NotBlank(message = "Username must not be blank")
    @Schema(description = "Unique account username", example = "jsmith", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Schema(description = "Account password, 8-72 characters", example = "correcthorsebattery",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
