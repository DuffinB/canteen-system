package edu.campus.canteen.controller;

import edu.campus.canteen.config.SecurityConfig;
import edu.campus.canteen.dto.DishCreateRequest;
import edu.campus.canteen.security.ApiAccessDeniedHandler;
import edu.campus.canteen.security.ApiAuthenticationEntryPoint;
import edu.campus.canteen.security.JwtAuthenticationFilter;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.DishService;
import edu.campus.canteen.vo.DishVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminDishController.class, properties =
        "security.jwt.secret=test-only-secret-key-with-more-than-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class AdminDishSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockBean private DishService dishService;

    @Test
    void anonymousUserCannotCreateDish() throws Exception {
        mockMvc.perform(post("/api/admin/dishes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void studentCannotCreateDish() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanCreateDish() throws Exception {
        when(dishService.createDish(any(DishCreateRequest.class))).thenReturn(new DishVO(
                9L, 3L, "主食", "盖饭", null, null, new BigDecimal("16.50"), 5,
                "OFF_SALE", null, null));
        String token = jwtService.generateToken(1L, "ADMIN");

        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
    }

    @Test
    void adminRequestWithNegativePriceIsRejected() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");
        String request = """
                {"categoryId":3,"name":"盖饭","price":-1,"stock":5}
                """;

        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(dishService);
    }

    @Test
    void adminRequestWithNegativeStockIsRejected() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");
        String request = """
                {"categoryId":3,"name":"盖饭","price":10,"stock":-1}
                """;

        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(dishService);
    }

    private String validRequest() {
        return """
                {"categoryId":3,"name":"盖饭","price":16.50,"stock":5}
                """;
    }
}
