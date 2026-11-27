package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.UserProfileUpdateRequest;
import edu.campus.canteen.security.JwtAuthenticationPrincipal;
import edu.campus.canteen.service.UserService;
import edu.campus.canteen.vo.UserInfoVO;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping
    public ApiResponse<UserInfoVO> updateProfile(Authentication authentication,
                                                  @Valid @RequestBody UserProfileUpdateRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(userService.updateCurrentUserProfile(principal.userId(), request));
    }
}
