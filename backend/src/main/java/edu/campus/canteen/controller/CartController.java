package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.CartAddRequest;
import edu.campus.canteen.dto.CartQuantityRequest;
import edu.campus.canteen.security.JwtAuthenticationPrincipal;
import edu.campus.canteen.service.CartService;
import edu.campus.canteen.vo.CartVO;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartVO> getCart(Authentication authentication) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(cartService.getCurrentUserCart(principal.userId()));
    }

    @PostMapping("/items")
    public ApiResponse<CartVO> addItem(Authentication authentication,
                                       @Valid @RequestBody CartAddRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(cartService.addItem(principal.userId(), request));
    }

    @PutMapping("/items/{dishId}")
    public ApiResponse<CartVO> updateItemQuantity(Authentication authentication,
                                                   @PathVariable Long dishId,
                                                   @Valid @RequestBody CartQuantityRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(cartService.updateItemQuantity(principal.userId(), dishId, request));
    }

    @DeleteMapping("/items/{dishId}")
    public ApiResponse<Void> removeItem(Authentication authentication, @PathVariable Long dishId) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        cartService.removeItem(principal.userId(), dishId);
        return ApiResponse.success(null);
    }
}
