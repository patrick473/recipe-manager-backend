package com.example.recipemanager.controller;

import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.model.User;
import com.example.recipemanager.security.JwtService;
import com.example.recipemanager.security.ProblemDetailAccessDeniedHandler;
import com.example.recipemanager.security.ProblemDetailAuthenticationEntryPoint;
import com.example.recipemanager.security.SecurityConfig;
import com.example.recipemanager.security.UserDetailsServiceImpl;
import com.example.recipemanager.security.UserPrincipal;
import com.example.recipemanager.service.RecipeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for {@code GET /recipes}: parameter parsing/defaults,
 * {@code tags} comma-splitting, {@code size} clamping, nulls-last sort
 * construction, and the 400 response for an unknown {@code sort} field.
 * {@link com.example.recipemanager.repository.RecipeSpecifications} query
 * behavior itself is covered by {@code RecipeRepositoryTest}, not re-verified
 * here since {@link RecipeService} is mocked.
 * <p>
 * Once {@code spring-boot-starter-security} is on the classpath, {@code @WebMvcTest}
 * auto-secures its slice: every request needs an authenticated principal, attached
 * here via a {@code spring-security-test} {@link RequestPostProcessor} wrapping a
 * {@link UserPrincipal} (rather than the library's built-in {@code user(...)}, which
 * produces a plain {@code org.springframework.security.core.userdetails.User} that
 * isn't assignable to the {@code @AuthenticationPrincipal UserPrincipal} parameter
 * {@code RecipeController} declares).
 * <p>
 * {@code @WebMvcTest} slices don't load any {@code @Configuration} class by
 * default (only controllers/advice/filters/etc.), so without importing
 * {@link SecurityConfig} the slice never activates Spring Security's MVC
 * integration (in particular {@code AuthenticationPrincipalArgumentResolver})
 * — {@code @AuthenticationPrincipal} then silently falls back to Spring MVC's
 * generic model-attribute resolver, which instantiates {@link UserPrincipal}
 * via reflection (bypassing its constructor) instead of reading the real
 * principal, leaving every field null. Importing {@code SecurityConfig} (and
 * the two {@code ProblemDetail} handlers it depends on) fixes that; the
 * handful of collaborators it wires (JWT/user-lookup) are mocked since this
 * is still a slice test, not a full-stack one.
 */
@WebMvcTest(RecipeController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
class RecipeControllerTest {

    private static final Long OWNER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    /**
     * Simulates an authenticated request for {@code OWNER_ID}/"testuser" by
     * setting a {@link UserPrincipal}-backed authentication token directly
     * on the request's security context.
     */
    private static RequestPostProcessor authenticatedUser() {
        User user = User.builder().id(OWNER_ID).username("testuser").password("hash").build();
        UserPrincipal principal = new UserPrincipal(user);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static RecipePageResponse emptyPageResponse() {
        return RecipePageResponse.builder()
                .content(List.of())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .build();
    }

    @Test
    void listAllUsesDefaultsWhenNoParamsGiven() throws Exception {
        when(service.findAll(isNull(), isNull(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(isNull(), isNull(), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);

        Sort.Order order = pageable.getSort().getOrderFor("title");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listAllPassesQAndCommaSeparatedTagsThroughToService() throws Exception {
        when(service.findAll(eq("banana"), eq(List.of("quick", "dessert")), any(Pageable.class)))
                .thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("q", "banana").param("tags", "quick,dessert").with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(service).findAll(eq("banana"), eq(List.of("quick", "dessert")), any(Pageable.class));
    }

    @Test
    void listAllTreatsEmptyQAndTagsAsAbsent() throws Exception {
        when(service.findAll(eq(""), eq(List.of()), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("q", "").param("tags", "").with(authenticatedUser()))
                .andExpect(status().isOk());

        // Spring's comma-list conversion turns an empty "tags=" into an empty list;
        // downstream RecipeSpecifications.hasAnyTag() treats null/empty/blank the same way.
        verify(service).findAll(eq(""), eq(List.of()), any(Pageable.class));
    }

    @Test
    void listAllParsesSortFieldAndDirectionWithNullsLastForNumericFields() throws Exception {
        when(service.findAll(any(), any(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("sort", "prepTimeMinutes,desc").with(authenticatedUser()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(any(), any(), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("prepTimeMinutes");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(order.getNullHandling()).isEqualTo(Sort.NullHandling.NULLS_LAST);
    }

    @Test
    void listAllDoesNotApplyNullsLastToNonNumericFields() throws Exception {
        when(service.findAll(any(), any(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("sort", "createdAt,desc").with(authenticatedUser()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(any(), any(), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(order.getNullHandling()).isEqualTo(Sort.NullHandling.NATIVE);
    }

    @Test
    void listAllClampsSizeAbove100() throws Exception {
        when(service.findAll(any(), any(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("size", "500").with(authenticatedUser()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void listAllPassesPageThrough() throws Exception {
        when(service.findAll(any(), any(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("page", "3").with(authenticatedUser()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
    }

    @Test
    void listAllReturns400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/recipes").param("sort", "bogusField,asc").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://example.com/errors/invalid-sort-field"))
                .andExpect(jsonPath("$.title").value("Invalid Sort Field"))
                .andExpect(jsonPath("$.detail").value("Unknown sort field: bogusField"));

        verifyNoInteractions(service);
    }

    @Test
    void listAllWithNoTokenSucceeds() throws Exception {
        when(service.findAll(isNull(), isNull(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes")).andExpect(status().isOk());
    }

    @Test
    void getByIdWithNoTokenSucceeds() throws Exception {
        when(service.findById(1L)).thenReturn(RecipeResponse.builder().id(1L).title("Banana Bread").build());

        mockMvc.perform(get("/recipes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Banana Bread"));
    }
}
