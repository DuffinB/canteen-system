package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.AdminOrderQueryRequest;
import edu.campus.canteen.security.JwtAuthenticationPrincipal;
import edu.campus.canteen.service.OrderService;
import edu.campus.canteen.vo.AdminOrderDetailVO;
import edu.campus.canteen.vo.AdminOrderSummaryVO;
import edu.campus.canteen.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageVO<AdminOrderSummaryVO>> list(@Valid @ModelAttribute AdminOrderQueryRequest query) {
        return ApiResponse.success(orderService.getAdminOrders(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminOrderDetailVO> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(orderService.getAdminOrder(id));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(Authentication authentication, @PathVariable @Positive Long id) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        orderService.confirmAdminOrder(principal.userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/ready")
    public ApiResponse<Void> markReady(Authentication authentication, @PathVariable @Positive Long id) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        orderService.markAdminOrderReady(principal.userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<Void> complete(Authentication authentication, @PathVariable @Positive Long id) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        orderService.completeAdminOrder(principal.userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(Authentication authentication, @PathVariable @Positive Long id) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        orderService.cancelAdminOrder(principal.userId(), id);
        return ApiResponse.success(null);
    }
}
