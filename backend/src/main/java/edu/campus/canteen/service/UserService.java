package edu.campus.canteen.service;

import edu.campus.canteen.dto.LoginRequest;
import edu.campus.canteen.dto.RegisterRequest;
import edu.campus.canteen.dto.UserProfileUpdateRequest;
import edu.campus.canteen.dto.AdminUserQueryRequest;
import edu.campus.canteen.dto.UserStatusRequest;
import edu.campus.canteen.vo.LoginResponseVO;
import edu.campus.canteen.vo.UserInfoVO;
import edu.campus.canteen.vo.AdminUserVO;
import edu.campus.canteen.vo.PageVO;

public interface UserService {
    UserInfoVO register(RegisterRequest request);

    LoginResponseVO login(LoginRequest request);

    UserInfoVO getCurrentUser(Long userId);

    UserInfoVO updateCurrentUserProfile(Long userId, UserProfileUpdateRequest request);

    PageVO<AdminUserVO> getAdminUsers(AdminUserQueryRequest query);

    AdminUserVO updateAdminUserStatus(Long adminId, Long userId, UserStatusRequest request);
}
