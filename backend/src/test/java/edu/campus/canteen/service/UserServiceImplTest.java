package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.dto.LoginRequest;
import edu.campus.canteen.dto.RegisterRequest;
import edu.campus.canteen.dto.UserProfileUpdateRequest;
import edu.campus.canteen.entity.User;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.UserMapper;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    private static final String TEST_SECRET = "test-only-secret-key-with-more-than-32-bytes";

    @Mock
    private UserMapper userMapper;

    private BCryptPasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(TEST_SECRET);
        userService = new UserServiceImpl(userMapper, passwordEncoder, jwtService);
    }

    @Test
    void registerShouldHashPasswordAndForceStudentDefaults() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        });
        when(userMapper.selectById(7L)).thenAnswer(invocation -> {
            var captor = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(captor.capture());
            return captor.getValue();
        });

        var result = userService.register(new RegisterRequest(" 20260007 ", "secret-pass-123", " 小林 "));

        assertEquals("20260007", result.studentNo());
        assertEquals("小林", result.nickname());
        assertEquals("STUDENT", result.role());
        verify(userMapper).insert((User) argThat((User user) -> user.getPasswordHash() != null
                && !"secret-pass-123".equals(user.getPasswordHash())
                && passwordEncoder.matches("secret-pass-123", user.getPasswordHash())
                && Integer.valueOf(1).equals(user.getStatus())
                && Integer.valueOf(0).equals(user.getDeleted())));
    }

    @Test
    void registerShouldRejectExistingStudentNumber() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.register(new RegisterRequest("20260007", "secret-pass-123", "小林")));

        assertEquals(ErrorCode.DUPLICATE_ACCOUNT, exception.getErrorCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void registerShouldTranslateConcurrentUniqueKeyConflict() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.register(new RegisterRequest("20260007", "secret-pass-123", "小林")));

        assertEquals(ErrorCode.DUPLICATE_ACCOUNT, exception.getErrorCode());
    }

    @Test
    void loginShouldReturnTokenForValidCredentials() {
        User user = user("20260007", "STUDENT", 1, passwordEncoder.encode("secret-pass-123"));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        var response = userService.login(new LoginRequest("20260007", "secret-pass-123"));

        assertEquals(7L, jwtService.parseToken(response.token()).userId());
        assertEquals("20260007", response.user().studentNo());
    }

    @Test
    void loginShouldRejectWrongPasswordAndUnknownAccountWithSameError() {
        User user = user("20260007", "STUDENT", 1, passwordEncoder.encode("secret-pass-123"));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user).thenReturn(null);

        BusinessException wrongPassword = assertThrows(BusinessException.class,
                () -> userService.login(new LoginRequest("20260007", "wrong-password")));
        BusinessException unknownAccount = assertThrows(BusinessException.class,
                () -> userService.login(new LoginRequest("unknown", "secret-pass-123")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrongPassword.getErrorCode());
        assertEquals(wrongPassword.getErrorCode(), unknownAccount.getErrorCode());
    }

    @Test
    void loginShouldRejectDisabledAccount() {
        User user = user("20260007", "STUDENT", 0, passwordEncoder.encode("secret-pass-123"));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login(new LoginRequest("20260007", "secret-pass-123")));

        assertEquals(ErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
    }

    @Test
    void profileUpdateChangesOnlyCurrentUsersNickname() {
        User before = user("20260007", "STUDENT", 1, passwordEncoder.encode("secret-pass-123"));
        before.setNickname("小林");
        User after = user("20260007", "STUDENT", 1, before.getPasswordHash());
        after.setNickname("新昵称");
        when(userMapper.selectById(7L)).thenReturn(before, after);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        var result = userService.updateCurrentUserProfile(7L, new UserProfileUpdateRequest(" 新昵称 "));

        assertEquals("新昵称", result.nickname());
        assertEquals("20260007", result.studentNo());
        assertEquals("STUDENT", result.role());
        org.mockito.ArgumentCaptor<User> updateCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(updateCaptor.capture());
        assertEquals(7L, updateCaptor.getValue().getId());
        assertEquals("新昵称", updateCaptor.getValue().getNickname());
        assertNull(updateCaptor.getValue().getStudentNo());
        assertNull(updateCaptor.getValue().getRole());
        assertNull(updateCaptor.getValue().getStatus());
        verify(userMapper, times(2)).selectById(7L);
    }

    private User user(String studentNo, String role, int status, String passwordHash) {
        User user = new User();
        user.setId(7L);
        user.setStudentNo(studentNo);
        user.setRole(role);
        user.setStatus(status);
        user.setPasswordHash(passwordHash);
        user.setDeleted(0);
        return user;
    }
}
