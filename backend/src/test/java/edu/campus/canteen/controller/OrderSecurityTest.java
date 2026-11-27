package edu.campus.canteen.controller;

import edu.campus.canteen.config.SecurityConfig;
import edu.campus.canteen.controller.AdminOrderController;
import edu.campus.canteen.dto.AdminOrderQueryRequest;
import edu.campus.canteen.dto.OrderCreateRequest;
import edu.campus.canteen.dto.OrderQueryRequest;
import edu.campus.canteen.security.ApiAccessDeniedHandler;
import edu.campus.canteen.security.ApiAuthenticationEntryPoint;
import edu.campus.canteen.security.JwtAuthenticationFilter;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.OrderService;
import edu.campus.canteen.vo.OrderVO;
import edu.campus.canteen.vo.AdminOrderDetailVO;
import edu.campus.canteen.vo.AdminOrderSummaryVO;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OrderController.class, AdminOrderController.class}, properties =
        "security.jwt.secret=test-only-secret-key-with-more-than-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class OrderSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockBean private OrderService orderService;

    @Test
    void anonymousUserCannotSubmitOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(orderService);
    }

    @Test
    void adminCannotSubmitStudentOrder() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "key-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(orderService);
    }

    @Test
    void studentCanSubmitOrderUsingUserIdFromJwt() throws Exception {
        when(orderService.submitOrder(eq(7L), eq("key-student"), any(OrderCreateRequest.class)))
                .thenReturn(new OrderVO(20L, "test-order-number", new BigDecimal("13.80"), "PENDING",
                        LocalDate.now().plusDays(1), "LUNCH", null, List.of(), null));
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "key-student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(13.80));

        verify(orderService).submitOrder(eq(7L), eq("key-student"), any(OrderCreateRequest.class));
    }

    @Test
    void studentOrderListRejectsUnsupportedStatus() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    void invalidPickupSlotIsRejected() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "key-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupDate\":\"2099-01-01\",\"pickupSlot\":\"NIGHT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    void studentCanListOwnOrdersWithPagination() throws Exception {
        when(orderService.getMyOrders(eq(7L), any(OrderQueryRequest.class)))
                .thenReturn(new PageVO<>(List.of(), 1, 2, 5));
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(orderService).getMyOrders(eq(7L), any(OrderQueryRequest.class));
    }

    @Test
    void studentCanReadOwnOrderDetailWithItemSnapshots() throws Exception {
        when(orderService.getMyOrder(7L, 35L)).thenReturn(new OrderVO(35L, "order-number",
                new BigDecimal("13.80"), "PENDING", LocalDate.now().plusDays(1), "LUNCH", null,
                List.of(new edu.campus.canteen.vo.OrderItemVO(9L, "下单快照菜名", new BigDecimal("13.80"),
                        1, new BigDecimal("13.80"))), null));
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(get("/api/orders/35").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].dishName").value("下单快照菜名"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(13.80))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(13.80));

        verify(orderService).getMyOrder(7L, 35L);
    }

    @Test
    void anonymousUserCannotReadOrdersOrOrderDetails() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get("/api/orders/35"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(put("/api/orders/35/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(orderService);
    }

    @Test
    void adminCannotReadStudentOrderEndpoints() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/35").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/orders/35/cancel").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void studentCancellationUsesUserIdFromJwt() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(put("/api/orders/35/cancel").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(orderService).cancelMyOrder(7L, 35L);
    }

    @Test
    void studentCanConfirmPickupUsingUserIdFromJwt() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(post("/api/orders/35/confirm-pickup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(orderService).confirmPickup(7L, 35L);
    }

    @Test
    void anonymousAndAdminCannotConfirmStudentPickup() throws Exception {
        mockMvc.perform(post("/api/orders/35/confirm-pickup"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        String adminToken = jwtService.generateToken(1L, "ADMIN");
        mockMvc.perform(post("/api/orders/35/confirm-pickup")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(orderService);
    }

    @Test
    void adminCanListAllOrdersAndFilterByStatus() throws Exception {
        when(orderService.getAdminOrders(any(AdminOrderQueryRequest.class)))
                .thenReturn(new PageVO<>(List.of(new AdminOrderSummaryVO(35L, "order-35", 7L,
                        "student-001", "学生甲", new BigDecimal("13.80"), "PENDING",
                        LocalDate.now().plusDays(1), "LUNCH", null, null)), 1, 1, 10));
        String token = jwtService.generateToken(1L, "ADMIN");

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)
                        .param("page", "1").param("size", "10").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].studentNo").value("student-001"))
                .andExpect(jsonPath("$.data.records[0].studentNickname").value("学生甲"))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));
        org.mockito.ArgumentCaptor<AdminOrderQueryRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AdminOrderQueryRequest.class);
        verify(orderService).getAdminOrders(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", captor.getValue().status());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk());
        verify(orderService, org.mockito.Mockito.times(2)).getAdminOrders(any(AdminOrderQueryRequest.class));
    }

    @Test
    void adminCanCancelOrderButStudentAndAnonymousCannot() throws Exception {
        mockMvc.perform(post("/api/admin/orders/35/cancel"))
                .andExpect(status().isUnauthorized());
        String studentToken = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(post("/api/admin/orders/35/cancel")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        String adminToken = jwtService.generateToken(8L, "ADMIN");
        mockMvc.perform(post("/api/admin/orders/35/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(orderService).cancelAdminOrder(8L, 35L);
    }

    @Test
    void adminCanReadOrderDetailWithSnapshotItems() throws Exception {
        when(orderService.getAdminOrder(35L)).thenReturn(new AdminOrderDetailVO(35L, "order-35", 7L,
                "student-001", "学生甲", new BigDecimal("27.60"), "CANCELLED",
                LocalDate.now().plusDays(1), "LUNCH", null, 1L, null, null,
                List.of(new edu.campus.canteen.vo.OrderItemVO(9L, "下单时菜名", new BigDecimal("13.80"),
                        2, new BigDecimal("27.60")))));
        String token = jwtService.generateToken(1L, "ADMIN");

        mockMvc.perform(get("/api/admin/orders/35").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentNo").value("student-001"))
                .andExpect(jsonPath("$.data.items[0].dishName").value("下单时菜名"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(13.80))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(27.60));
        verify(orderService).getAdminOrder(35L);
    }

    @Test
    void studentAndAnonymousCannotAccessAdminOrders() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/orders/35").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void adminOrderListRejectsInvalidStatusAndPagination() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)
                        .param("page", "100001"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void adminOrderListAcceptsMaximumPageAndSize() throws Exception {
        when(orderService.getAdminOrders(any(AdminOrderQueryRequest.class)))
                .thenReturn(new PageVO<>(List.of(), 0, 100000, 100));
        String token = jwtService.generateToken(1L, "ADMIN");
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)
                        .param("page", "100000").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(100000))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void adminOrderDetailMissingReturnsUnifiedNotFound() throws Exception {
        when(orderService.getAdminOrder(999L)).thenThrow(new edu.campus.canteen.exception.BusinessException(
                edu.campus.canteen.exception.ErrorCode.RESOURCE_NOT_FOUND));
        String token = jwtService.generateToken(1L, "ADMIN");
        mockMvc.perform(get("/api/admin/orders/999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void adminCanConfirmOrderUsingAdminIdFromJwt() throws Exception {
        String token = jwtService.generateToken(8L, "ADMIN");
        mockMvc.perform(post("/api/admin/orders/35/confirm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(orderService).confirmAdminOrder(8L, 35L);
    }

    @Test
    void anonymousAndStudentCannotConfirmAdminOrder() throws Exception {
        mockMvc.perform(post("/api/admin/orders/35/confirm"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        String studentToken = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(post("/api/admin/orders/35/confirm")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(orderService);
    }

    @Test
    void adminCanMarkOrderReadyUsingAdminIdFromJwt() throws Exception {
        String token = jwtService.generateToken(8L, "ADMIN");
        mockMvc.perform(post("/api/admin/orders/35/ready")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(orderService).markAdminOrderReady(8L, 35L);
    }

    @Test
    void anonymousAndStudentCannotMarkOrderReady() throws Exception {
        mockMvc.perform(post("/api/admin/orders/35/ready"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(post("/api/admin/orders/35/ready")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(orderService);
    }

    @Test
    void adminCanCompleteOrderUsingAdminIdFromJwt() throws Exception {
        String token = jwtService.generateToken(8L, "ADMIN");
        mockMvc.perform(post("/api/admin/orders/35/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(orderService).completeAdminOrder(8L, 35L);
    }

    @Test
    void anonymousAndStudentCannotCompleteAdminOrder() throws Exception {
        mockMvc.perform(post("/api/admin/orders/35/complete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(post("/api/admin/orders/35/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(orderService);
    }

    @Test
    void invalidOrderListPageBoundariesAreRejected() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("page", "100001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    void orderListAllowsMaximumPageAndSize() throws Exception {
        when(orderService.getMyOrders(eq(7L), any(OrderQueryRequest.class)))
                .thenReturn(new PageVO<>(List.of(), 0, 100000, 100));
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("page", "100000").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(100000))
                .andExpect(jsonPath("$.data.size").value(100));
        verify(orderService).getMyOrders(eq(7L), any(OrderQueryRequest.class));
    }

    private String validRequest() {
        return "{\"pickupDate\":\"2099-01-01\",\"pickupSlot\":\"LUNCH\",\"remark\":\"少辣\"}";
    }
}
