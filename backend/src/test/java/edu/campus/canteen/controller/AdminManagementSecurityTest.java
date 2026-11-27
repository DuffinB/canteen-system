package edu.campus.canteen.controller;

import edu.campus.canteen.config.SecurityConfig;
import edu.campus.canteen.security.ApiAccessDeniedHandler;
import edu.campus.canteen.security.ApiAuthenticationEntryPoint;
import edu.campus.canteen.security.JwtAuthenticationFilter;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.AdminStatisticsService;
import edu.campus.canteen.service.CategoryService;
import edu.campus.canteen.service.UserService;
import edu.campus.canteen.vo.AdminCategoryVO;
import edu.campus.canteen.vo.AdminStatisticsSummaryVO;
import edu.campus.canteen.vo.AdminDishSalesVO;
import edu.campus.canteen.vo.AdminUserVO;
import edu.campus.canteen.vo.PageVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminCategoryController.class, AdminUserController.class, AdminStatisticsController.class},
        properties = "security.jwt.secret=test-only-secret-key-with-more-than-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class AdminManagementSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockBean private CategoryService categoryService;
    @MockBean private UserService userService;
    @MockBean private AdminStatisticsService statisticsService;

    @Test
    void anonymousCannotAccessAdminManagement() throws Exception {
        mockMvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/statistics/summary").param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService, userService, statisticsService);
    }

    @Test
    void studentCannotAccessAdminManagement() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(get("/api/admin/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/statistics/summary").header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-01-01").param("endDate", "2026-01-31"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categoryService, userService, statisticsService);
    }

    @Test
    void adminCanAccessAdminListsAndSummary() throws Exception {
        String token = jwtService.generateToken(2L, "ADMIN");
        when(categoryService.getAdminCategories()).thenReturn(List.of(
                new AdminCategoryVO(1L, "主食", 1, 1, null, null)));
        when(userService.getAdminUsers(any())).thenReturn(new PageVO<>(
                List.of(new AdminUserVO(7L, "s001", "学生", "STUDENT", 1, null)), 1, 1, 10));
        when(statisticsService.getSummary(any())).thenReturn(new AdminStatisticsSummaryVO(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), 2, 1, new BigDecimal("12.50")));
        when(statisticsService.getDishSales(any())).thenReturn(List.of(new AdminDishSalesVO(9L, "盖饭", 6)));

        mockMvc.perform(get("/api/admin/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value(1));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].studentNo").value("s001"))
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/admin/statistics/summary").header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-01-01").param("endDate", "2026-01-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.completedSales").value(12.50));
        mockMvc.perform(get("/api/admin/statistics/dishes").header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-01-01").param("endDate", "2026-01-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].salesQuantity").value(6));
    }

    @Test
    void statisticsRejectsReversedDateRange() throws Exception {
        String token = jwtService.generateToken(2L, "ADMIN");
        mockMvc.perform(get("/api/admin/statistics/summary").header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-02-01").param("endDate", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void studentCannotAccessDishSalesStatistics() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(get("/api/admin/statistics/dishes").header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-01-01").param("endDate", "2026-01-31"))
                .andExpect(status().isForbidden());
    }
}
