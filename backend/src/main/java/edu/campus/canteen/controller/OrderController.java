package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.OrderCreateRequest;
import edu.campus.canteen.dto.OrderQueryRequest;
import edu.campus.canteen.security.JwtAuthenticationPrincipal;
import edu.campus.canteen.service.OrderService;
import edu.campus.canteen.vo.OrderVO;
import edu.campus.canteen.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderVO> submitOrder(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(orderService.submitOrder(principal.userId(), idempotencyKey, request));
    }

    @GetMapping
    public ApiResponse<PageVO<OrderVO>> getMyOrders(Authentication authentication,
                                                     @Valid @ModelAttribute OrderQueryRequest query) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(orderService.getMyOrders(principal.userId(), query));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVO> getMyOrder(Authentication authentication,
                                           @PathVariable @Positive Long id) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(orderService.getMyOrder(principal.userId(), id));
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancelMyOrder(Authentication authentication, @PathVariable @Positive Long id) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        orderService.cancelMyOrder(principal.userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/confirm-pickup")
    public ApiResponse<Void> confirmPickup(Authentication authentication, @PathVariable @Positive Long id) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        orderService.confirmPickup(principal.userId(), id);
        return ApiResponse.success(null);
    }
}
