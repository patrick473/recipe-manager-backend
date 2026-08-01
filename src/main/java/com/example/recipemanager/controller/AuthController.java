package com.example.recipemanager.controller;

import com.example.recipemanager.dto.AuthResponse;
import com.example.recipemanager.dto.LoginRequest;
import com.example.recipemanager.dto.RegisterRequest;
import com.example.recipemanager.exception.UsernameAlreadyExistsException;
import com.example.recipemanager.model.User;
import com.example.recipemanager.repository.UserRepository;
import com.example.recipemanager.security.JwtService;
import com.example.recipemanager.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints for creating an account and exchanging credentials for a JWT.
 */
@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register a new account or log into an existing one")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Operation(summary = "Register a new account",
               description = "Creates the account and immediately logs it in, returning a token in the "
                       + "same shape as `POST /auth/login` — no separate login call is required afterward.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created",
                     content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Blank username or password shorter than 8 characters",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Username already taken",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        User user = userRepository.save(User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .build());

        return toAuthResponse(user);
    }

    @Operation(summary = "Log into an existing account",
               description = "Wrong username or wrong password both produce the same 401 — the response "
                       + "never reveals which one was incorrect.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated",
                     content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Wrong username or password",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        // Spring Security throws BadCredentialsException on a bad username/password —
        // handled centrally by ProblemDetailAuthenticationEntryPoint / SecurityConfig
        // (Part 3), not here, so both failure modes collapse to the same 401.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return toAuthResponse(principal.getUser());
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.builder()
                .token(jwtService.generateToken(user))
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }
}
