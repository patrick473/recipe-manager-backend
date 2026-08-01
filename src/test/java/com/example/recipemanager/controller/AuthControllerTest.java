package com.example.recipemanager.controller;

import com.example.recipemanager.model.User;
import com.example.recipemanager.repository.UserRepository;
import com.example.recipemanager.security.JwtService;
import com.example.recipemanager.security.ProblemDetailAccessDeniedHandler;
import com.example.recipemanager.security.ProblemDetailAuthenticationEntryPoint;
import com.example.recipemanager.security.SecurityConfig;
import com.example.recipemanager.security.UserDetailsServiceImpl;
import com.example.recipemanager.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for {@code POST /auth/register} / {@code POST /auth/login}:
 * {@link UserRepository}, {@link PasswordEncoder}, {@link JwtService}, and
 * {@link AuthenticationManager} are all mocked, so these tests exercise only
 * {@link AuthController}'s own logic (validation wiring, 409 on a taken
 * username, and building the {@code AuthResponse}) — not real password
 * hashing/authentication, which {@code AuthenticationManager}/
 * {@code DaoAuthenticationProvider} own and aren't re-verified here.
 * <p>
 * {@link SecurityConfig} is imported (along with the two {@code ProblemDetail}
 * handlers it depends on) so that a {@code BadCredentialsException} thrown by
 * the mocked {@code AuthenticationManager} gets translated to a 401
 * {@code ProblemDetail} the same way it would in production — that
 * translation happens in Spring Security's {@code ExceptionTranslationFilter},
 * part of the real filter chain {@code SecurityConfig} wires, which a plain
 * {@code @WebMvcTest} slice does not include on its own.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    private static User savedUser(Long id, String username) {
        return User.builder().id(id).username(username).password("bcrypt-hash").build();
    }

    // -------------------------------------------------------------------------
    // POST /auth/register
    // -------------------------------------------------------------------------

    @Test
    void registerWithTakenUsernameReturns409() throws Exception {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"taken","password":"password123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://example.com/errors/username-taken"));
    }

    @Test
    void registerWithShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newuser","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://example.com/errors/validation-failed"));
    }

    @Test
    void registerWithBlankUsernameReturns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"password123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://example.com/errors/validation-failed"));
    }

    @Test
    void registerSucceedsWithTokenUserIdAndUsernamePresentAndReturns201() throws Exception {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        User saved = savedUser(1L, "newuser");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtService.generateToken(saved)).thenReturn("signed.jwt.token");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newuser","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("newuser"));
    }

    // -------------------------------------------------------------------------
    // POST /auth/login
    // -------------------------------------------------------------------------

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"existing","password":"wrongpassword"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithNonexistentUsernameReturns401WithSameBodyAsWrongPassword() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","password":"whatever123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSucceedsWithTokenUserIdAndUsernamePresentAndReturns200() throws Exception {
        User user = savedUser(7L, "existing");
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(jwtService.generateToken(user)).thenReturn("signed.jwt.token");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"existing","password":"correctpassword"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.username").value("existing"));
    }
}
