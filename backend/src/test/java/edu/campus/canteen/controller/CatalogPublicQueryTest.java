package edu.campus.canteen.controller;

import edu.campus.canteen.config.SecurityConfig;
import edu.campus.canteen.security.ApiAccessDeniedHandler;
import edu.campus.canteen.security.ApiAuthenticationEntryPoint;
import edu.campus.canteen.security.JwtAuthenticationFilter;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.CategoryService;
import edu.campus.canteen.service.DishService;
import edu.campus.canteen.vo.CategoryVO;
import edu.campus.canteen.vo.DishVO;
import edu.campus.canteen.vo.PageVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CategoryController.class, DishController.class}, properties =
        "security.jwt.secret=test-only-secret-key-with-more-than-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class CatalogPublicQueryTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private DishService dishService;

    @Test
    void categoriesShouldBeAvailableWithoutToken() throws Exception {
        when(categoryService.getPublicCategories()).thenReturn(List.of(new CategoryVO(2L, "主食", 1)));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("主食"));
    }

    @Test
    void dishListShouldBeAvailableWithoutTokenAndBindFilters() throws Exception {
        when(dishService.getPublicDishes(any())).thenReturn(new PageVO<>(List.of(
                new DishVO(5L, 2L, "主食", "盖饭", "介绍", null,
                        new BigDecimal("16.50"), 9, "ON_SALE", null, null)), 1, 1, 10));

        mockMvc.perform(get("/api/dishes")
                        .param("page", "1")
                        .param("size", "10")
                        .param("categoryId", "2")
                        .param("keyword", "盖饭")
                        .param("sort", "priceAsc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("盖饭"));

        verify(dishService).getPublicDishes(any());
    }

    @Test
    void dishDetailShouldBeAvailableWithoutToken() throws Exception {
        when(dishService.getPublicDishById(5L)).thenReturn(new DishVO(5L, 2L, "主食", "盖饭", "介绍", null,
                new BigDecimal("16.50"), 9, "ON_SALE", null, null));

        mockMvc.perform(get("/api/dishes/5").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(16.5));
    }

    @Test
    void hotDishesShouldBePublicAndReturnServiceResult() throws Exception {
        when(dishService.getHotDishes()).thenReturn(List.of());
        mockMvc.perform(get("/api/dishes/hot").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        verify(dishService).getHotDishes();
    }

    @Test
    void dishListShouldRejectOutOfRangePageSize() throws Exception {
        mockMvc.perform(get("/api/dishes").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void dishListShouldRejectPageBelowOne() throws Exception {
        mockMvc.perform(get("/api/dishes").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void dishListShouldRejectPageAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/dishes").param("page", "100001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void dishListShouldAcceptMaximumPageAndNormalPagination() throws Exception {
        when(dishService.getPublicDishes(any())).thenReturn(new PageVO<>(List.of(), 0, 100000, 10));

        mockMvc.perform(get("/api/dishes").param("page", "100000").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(100000))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void dishListShouldRejectUnsupportedSortValue() throws Exception {
        mockMvc.perform(get("/api/dishes").param("sort", "name;drop table dishes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
