package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.dto.LoginRequest;
import edu.campus.canteen.dto.RegisterRequest;
import edu.campus.canteen.dto.UserProfileUpdateRequest;
import edu.campus.canteen.dto.AdminUserQueryRequest;
import edu.campus.canteen.dto.UserStatusRequest;
import edu.campus.canteen.entity.User;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.UserMapper;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.UserService;
import edu.campus.canteen.vo.LoginResponseVO;
import edu.campus.canteen.vo.UserInfoVO;
import edu.campus.canteen.vo.AdminUserVO;
import edu.campus.canteen.vo.PageVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private static final String STUDENT_ROLE = "STUDENT";
    private static final int ENABLED = 1;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public UserInfoVO register(RegisterRequest request) {
        String studentNo = request.studentNo().trim();
        boolean exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, studentNo)) > 0;
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACCOUNT);
        }

        User user = new User();
        user.setStudentNo(studentNo);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setRole(STUDENT_ROLE);
        user.setStatus(ENABLED);
        user.setDeleted(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 唯一键是并发注册时的最终保护；不向客户端暴露数据库异常细节。
            throw new BusinessException(ErrorCode.DUPLICATE_ACCOUNT);
        }
        return toUserInfo(userMapper.selectById(user.getId()));
    }

    @Override
    public LoginResponseVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, request.studentNo().trim()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!Integer.valueOf(ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtService.generateToken(user.getId(), user.getRole());
        return new LoginResponseVO(token, toUserInfo(user));
    }

    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Integer.valueOf(ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return toUserInfo(user);
    }

    @Override
    @Transactional
    public UserInfoVO updateCurrentUserProfile(Long userId, UserProfileUpdateRequest request) {
        User current = userMapper.selectById(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Integer.valueOf(ENABLED).equals(current.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String nickname = request.nickname().trim();
        User update = new User();
        update.setId(userId);
        update.setNickname(nickname);
        userMapper.updateById(update);
        return getCurrentUser(userId);
    }

    @Override
    public PageVO<AdminUserVO> getAdminUsers(AdminUserQueryRequest query) {
        Page<User> page = userMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt).orderByDesc(User::getId));
        List<AdminUserVO> records = page.getRecords().stream()
                .map(user -> new AdminUserVO(user.getId(), user.getStudentNo(), user.getNickname(),
                        user.getRole(), user.getStatus(), user.getCreatedAt()))
                .toList();
        return new PageVO<>(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional
    public AdminUserVO updateAdminUserStatus(Long adminId, Long userId, UserStatusRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (adminId.equals(userId) && Integer.valueOf(0).equals(request.status())) {
            throw new BusinessException(ErrorCode.ADMIN_CANNOT_DISABLE_SELF);
        }
        user.setStatus(request.status());
        userMapper.updateById(user);
        User updated = userMapper.selectById(userId);
        return new AdminUserVO(updated.getId(), updated.getStudentNo(), updated.getNickname(),
                updated.getRole(), updated.getStatus(), updated.getCreatedAt());
    }

    private UserInfoVO toUserInfo(User user) {
        return new UserInfoVO(user.getId(), user.getStudentNo(), user.getNickname(),
                user.getRole(), user.getCreatedAt());
    }
}
