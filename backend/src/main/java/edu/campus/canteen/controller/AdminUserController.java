package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.AdminUserQueryRequest;
import edu.campus.canteen.dto.UserStatusRequest;
import edu.campus.canteen.security.JwtAuthenticationPrincipal;
import edu.campus.canteen.service.UserService;
import edu.campus.canteen.vo.AdminUserVO;
import edu.campus.canteen.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) { this.userService = userService; }

    @GetMapping
    public ApiResponse<PageVO<AdminUserVO>> list(@Valid @ModelAttribute AdminUserQueryRequest query) {
        return ApiResponse.success(userService.getAdminUsers(query));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserVO> updateStatus(Authentication authentication,
                                                  @PathVariable @Positive Long id,
                                                  @Valid @RequestBody UserStatusRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(userService.updateAdminUserStatus(principal.userId(), id, request));
    }
}
