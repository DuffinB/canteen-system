package edu.campus.canteen.controller;

import edu.campus.canteen.config.SecurityConfig;
import edu.campus.canteen.dto.AdminReviewQueryRequest;
import edu.campus.canteen.dto.ReviewAuditRequest;
import edu.campus.canteen.dto.ReviewCreateRequest;
import edu.campus.canteen.dto.ReviewPageRequest;
import edu.campus.canteen.security.ApiAccessDeniedHandler;
import edu.campus.canteen.security.ApiAuthenticationEntryPoint;
import edu.campus.canteen.security.JwtAuthenticationFilter;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.ReviewService;
import edu.campus.canteen.vo.AdminReviewVO;
import edu.campus.canteen.vo.DishReviewVO;
import edu.campus.canteen.vo.PageVO;
import edu.campus.canteen.vo.ReviewVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReviewController.class, AdminReviewController.class}, properties =
        "security.jwt.secret=test-only-secret-key-with-more-than-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class ReviewSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockBean private ReviewService reviewService;

    @Test
    void studentCanCreateReviewWithIdentityFromJwt() throws Exception {
        when(reviewService.create(eq(7L), any(ReviewCreateRequest.class)))
                .thenReturn(new ReviewVO(90L, 35L, 51L, 9L, 5, "很好吃", "PENDING", null,
                        LocalDateTime.now()));
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(post("/api/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":35,\"orderItemId\":51,\"dishId\":9,\"rating\":5,\"content\":\"很好吃\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(reviewService).create(eq(7L), any(ReviewCreateRequest.class));
    }

    @Test
    void studentCanEditOwnReviewUsingIdentityFromJwt() throws Exception {
        when(reviewService.update(eq(7L), eq(90L), any(edu.campus.canteen.dto.ReviewUpdateRequest.class)))
                .thenReturn(new ReviewVO(90L, 35L, 51L, 9L, 4, "更新内容", "PENDING", null,
                        LocalDateTime.now()));
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(put("/api/reviews/90").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"content\":\"更新内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(reviewService).update(eq(7L), eq(90L), any(edu.campus.canteen.dto.ReviewUpdateRequest.class));
    }

    @Test
    void anonymousAndAdminCannotEditStudentReview() throws Exception {
        String body = "{\"rating\":4,\"content\":\"更新内容\"}";
        mockMvc.perform(put("/api/reviews/90").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String adminToken = jwtService.generateToken(1L, "ADMIN");
        mockMvc.perform(put("/api/reviews/90").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reviewService);
    }

    @Test
    void anonymousAndAdminCannotSubmitStudentReview() throws Exception {
        String body = "{\"orderId\":35,\"orderItemId\":51,\"dishId\":9,\"rating\":5}";
        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String adminToken = jwtService.generateToken(1L, "ADMIN");
        mockMvc.perform(post("/api/reviews").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reviewService);
    }

    @Test
    void invalidRatingAndContentLengthAreRejected() throws Exception {
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(post("/api/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":35,\"orderItemId\":51,\"dishId\":9,\"rating\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        String longContent = "x".repeat(1001);
        mockMvc.perform(post("/api/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":35,\"orderItemId\":51,\"dishId\":9,\"rating\":5,\"content\":\""
                                + longContent + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reviewService);
    }

    @Test
    void studentCanListOwnReviewsAndAnonymousCanReadPublicDishReviews() throws Exception {
        when(reviewService.getMyReviews(eq(7L), any(ReviewPageRequest.class)))
                .thenReturn(new PageVO<>(List.of(), 0, 1, 10));
        when(reviewService.getApprovedDishReviews(eq(9L), any(ReviewPageRequest.class)))
                .thenReturn(new PageVO<>(List.of(new DishReviewVO(90L, 5, "很好吃", LocalDateTime.now())),
                        1, 1, 10));
        mockMvc.perform(get("/api/reviews/my"))
                .andExpect(status().isUnauthorized());
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(get("/api/reviews/my").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reviews/dish/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].rating").value(5));
        verify(reviewService).getMyReviews(eq(7L), any(ReviewPageRequest.class));
        verify(reviewService).getApprovedDishReviews(eq(9L), any(ReviewPageRequest.class));
    }

    @Test
    void adminCanListAndAuditReviewsUsingAdminIdFromJwt() throws Exception {
        when(reviewService.getAdminReviews(any(AdminReviewQueryRequest.class)))
                .thenReturn(new PageVO<>(List.of(new AdminReviewVO(90L, 7L, 9L, 35L, 51L,
                        5, "很好吃", "PENDING", null, null, LocalDateTime.now())), 1, 1, 10));
        String token = jwtService.generateToken(8L, "ADMIN");
        mockMvc.perform(get("/api/admin/reviews?page=1&size=10&status=PENDING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));
        mockMvc.perform(put("/api/admin/reviews/90/audit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        verify(reviewService).audit(eq(8L), eq(90L), any(ReviewAuditRequest.class));
    }

    @Test
    void studentAndAnonymousCannotAccessAdminReviewManagement() throws Exception {
        mockMvc.perform(get("/api/admin/reviews"))
                .andExpect(status().isUnauthorized());
        String token = jwtService.generateToken(7L, "STUDENT");
        mockMvc.perform(get("/api/admin/reviews").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/reviews/90/audit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reviewService);
    }
}
