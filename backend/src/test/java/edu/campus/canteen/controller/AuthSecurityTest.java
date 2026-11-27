package edu.campus.canteen.controller;

import edu.campus.canteen.config.SecurityConfig;
import edu.campus.canteen.dto.UserProfileUpdateRequest;
import edu.campus.canteen.security.ApiAccessDeniedHandler;
import edu.campus.canteen.security.ApiAuthenticationEntryPoint;
import edu.campus.canteen.security.JwtAuthenticationFilter;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.UserService;
import edu.campus.canteen.vo.UserInfoVO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class}, properties =
        "security.jwt.secret=test-only-secret-key-with-more-than-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class AuthSecurityTest {
    private static final String TEST_SECRET = "test-only-secret-key-with-more-than-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserService userService;

    @Test
    void registerEndpointAllowsAnonymousRequest() throws Exception {
        when(userService.register(any())).thenReturn(userInfo());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentNo":"20260001","password":"test-password-123","nickname":"测试用户"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.password_hash").doesNotExist());
    }

    @Test
    void loginEndpointAllowsAnonymousRequest() throws Exception {
        when(userService.login(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentNo":"20260001","password":"test-password-123"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void currentUserWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void currentUserWithValidTokenReturns200() throws Exception {
        when(userService.getCurrentUser(7L)).thenReturn(userInfo());
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void updateProfileUsesCurrentUserIdFromJwtAndReturnsSafeUserInfo() throws Exception {
        when(userService.updateCurrentUserProfile(eq(7L), any(UserProfileUpdateRequest.class)))
                .thenReturn(new UserInfoVO(7L, "20260001", "新昵称", "STUDENT", LocalDateTime.now()));
        String token = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"userId\":999,\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password_hash").doesNotExist());
        verify(userService).updateCurrentUserProfile(eq(7L),
                argThat(request -> request.nickname().equals("新昵称")));
    }

    @Test
    void updateProfileRejectsBlankAndOverlongNickname() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        String longNickname = "n".repeat(51);
        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + longNickname + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(userService);
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String expiredToken = Jwts.builder()
                .subject("7")
                .claim("role", "STUDENT")
                .issuedAt(new Date(now.getTime() - 10_000))
                .expiration(new Date(now.getTime() - 1_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void studentCannotAccessAdminEndpoint() throws Exception {
        String studentToken = jwtService.generateToken(7L, "STUDENT");

        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private UserInfoVO userInfo() {
        return new UserInfoVO(7L, "20260001", "测试用户", "STUDENT", LocalDateTime.now());
    }
}
