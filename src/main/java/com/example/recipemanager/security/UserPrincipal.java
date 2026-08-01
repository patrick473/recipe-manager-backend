package com.example.recipemanager.security;

import com.example.recipemanager.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a {@link User} entity to Spring Security's {@link UserDetails}
 * contract. Every account is granted a single fixed {@code ROLE_USER}
 * authority — there is no roles table (out of scope for this pass, see
 * AUTHENTICATION_SPEC.md's "Deferred" section).
 */
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    /**
     * The wrapped account. Not part of {@link UserDetails}; lets callers
     * (e.g. {@code AuthController}) get back the full entity without a
     * redundant repository lookup.
     */
    public User getUser() {
        return user;
    }

    /**
     * The account's surrogate key. Not part of {@link UserDetails}; used by
     * the controller layer to thread ownership through to {@code RecipeService}.
     */
    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}
