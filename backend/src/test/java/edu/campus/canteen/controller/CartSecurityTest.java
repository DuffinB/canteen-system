package edu.campus.canteen.controller;

import edu.campus.canteen.config.SecurityConfig;
import edu.campus.canteen.dto.CartAddRequest;
import edu.campus.canteen.dto.CartQuantityRequest;
import edu.campus.canteen.security.ApiAccessDeniedHandler;
import edu.campus.canteen.security.ApiAuthenticationEntryPoint;
import edu.campus.canteen.security.JwtAuthenticationFilter;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.CartService;
import edu.campus.canteen.vo.CartVO;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class, properties =
        "security.jwt.secret=test-only-secret-key-with-more-than-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class CartSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockBean private CartService cartService;

    @Test
    void anonymousUserCannotReadCart() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(cartService);
    }

    @Test
    void anonymousUserCannotAddCartItem() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\":9,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cartService);
    }

    @Test
    void studentCanReadOwnCartUsingUserIdFromJwt() throws Exception {
        when(cartService.getCurrentUserCart(12L)).thenReturn(new CartVO(List.of(), new BigDecimal("0.00")));
        String token = jwtService.generateToken(12L, "STUDENT");

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalAmount").value(0));

        verify(cartService).getCurrentUserCart(12L);
    }

    @Test
    void studentCanAddItemAndUserIdComesFromJwt() throws Exception {
        when(cartService.addItem(org.mockito.ArgumentMatchers.eq(12L), any(CartAddRequest.class)))
                .thenReturn(new CartVO(List.of(), new BigDecimal("0.00")));
        String token = jwtService.generateToken(12L, "STUDENT");

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\":9,\"quantity\":1}"))
                .andExpect(status().isOk());

        verify(cartService).addItem(org.mockito.ArgumentMatchers.eq(12L), any(CartAddRequest.class));
    }

    @Test
    void invalidQuantityIsRejectedByRequestValidation() throws Exception {
        String token = jwtService.generateToken(12L, "STUDENT");

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\":9,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(cartService);
    }

    @Test
    void adminCannotAccessStudentCart() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(cartService);
    }

    @Test
    void anonymousUserCannotUpdateCartItem() throws Exception {
        mockMvc.perform(put("/api/cart/items/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(cartService);
    }

    @Test
    void anonymousUserCannotDeleteCartItem() throws Exception {
        mockMvc.perform(delete("/api/cart/items/9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(cartService);
    }

    @Test
    void studentCanUpdateItemUsingUserIdFromJwt() throws Exception {
        when(cartService.updateItemQuantity(eq(12L), eq(9L), any(CartQuantityRequest.class)))
                .thenReturn(new CartVO(List.of(), new BigDecimal("0.00")));
        String token = jwtService.generateToken(12L, "STUDENT");

        mockMvc.perform(put("/api/cart/items/9")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk());

        verify(cartService).updateItemQuantity(eq(12L), eq(9L), any(CartQuantityRequest.class));
    }

    @Test
    void studentCanDeleteItemUsingUserIdFromJwt() throws Exception {
        String token = jwtService.generateToken(12L, "STUDENT");

        mockMvc.perform(delete("/api/cart/items/9")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(cartService).removeItem(12L, 9L);
    }

    @Test
    void updateWithInvalidQuantityIsRejected() throws Exception {
        String token = jwtService.generateToken(12L, "STUDENT");

        mockMvc.perform(put("/api/cart/items/9")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(cartService);
    }

    @Test
    void adminCannotModifyOrDeleteStudentCartItems() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");

        mockMvc.perform(put("/api/cart/items/9")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/cart/items/9")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }
}
