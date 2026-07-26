package com.example.recipemanager.controller;

import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.service.RecipeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
 */
@WebMvcTest(RecipeController.class)
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeService service;

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

        mockMvc.perform(get("/recipes"))
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

        mockMvc.perform(get("/recipes").param("q", "banana").param("tags", "quick,dessert"))
                .andExpect(status().isOk());

        verify(service).findAll(eq("banana"), eq(List.of("quick", "dessert")), any(Pageable.class));
    }

    @Test
    void listAllTreatsEmptyQAndTagsAsAbsent() throws Exception {
        when(service.findAll(eq(""), eq(List.of()), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("q", "").param("tags", ""))
                .andExpect(status().isOk());

        // Spring's comma-list conversion turns an empty "tags=" into an empty list;
        // downstream RecipeSpecifications.hasAnyTag() treats null/empty/blank the same way.
        verify(service).findAll(eq(""), eq(List.of()), any(Pageable.class));
    }

    @Test
    void listAllParsesSortFieldAndDirectionWithNullsLastForNumericFields() throws Exception {
        when(service.findAll(any(), any(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("sort", "prepTimeMinutes,desc"))
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

        mockMvc.perform(get("/recipes").param("sort", "createdAt,desc"))
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

        mockMvc.perform(get("/recipes").param("size", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void listAllPassesPageThrough() throws Exception {
        when(service.findAll(any(), any(), any(Pageable.class))).thenReturn(emptyPageResponse());

        mockMvc.perform(get("/recipes").param("page", "3"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
    }

    @Test
    void listAllReturns400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/recipes").param("sort", "bogusField,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://example.com/errors/invalid-sort-field"))
                .andExpect(jsonPath("$.title").value("Invalid Sort Field"))
                .andExpect(jsonPath("$.detail").value("Unknown sort field: bogusField"));

        verifyNoInteractions(service);
    }
}
