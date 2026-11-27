package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.campus.canteen.dto.AdminReviewQueryRequest;
import edu.campus.canteen.dto.ReviewAuditRequest;
import edu.campus.canteen.dto.ReviewCreateRequest;
import edu.campus.canteen.dto.ReviewUpdateRequest;
import edu.campus.canteen.dto.ReviewPageRequest;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.entity.Review;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.mapper.ReviewMapper;
import edu.campus.canteen.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {
    @Mock private ReviewMapper reviewMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewMapper, orderMapper, orderItemMapper);
    }

    @Test
    void studentCanReviewOwnCompletedOrderItem() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "COMPLETED"));
        when(orderItemMapper.selectOne(any(Wrapper.class))).thenReturn(item(51L, 35L, 9L));
        when(reviewMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(reviewMapper.insert(any(Review.class))).thenAnswer(invocation -> {
            Review inserted = invocation.getArgument(0);
            inserted.setId(90L);
            return 1;
        });
        when(reviewMapper.selectById(90L)).thenAnswer(invocation -> savedReview());

        var result = reviewService.create(7L, new ReviewCreateRequest(35L, 51L, 9L, 5, "很好吃"));

        assertEquals(90L, result.id());
        assertEquals("PENDING", result.status());
        assertEquals(5, result.rating());
        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        Review inserted = reviewCaptor.getValue();
        assertEquals(7L, inserted.getUserId());
        assertEquals(35L, inserted.getOrderId());
        assertEquals(51L, inserted.getOrderItemId());
        assertEquals(9L, inserted.getDishId());
        assertEquals("PENDING", inserted.getStatus());
    }

    @Test
    void anotherStudentsOrderIsHiddenAsNotFound() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> reviewService.create(7L, request()));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(orderItemMapper, reviewMapper);
    }

    @Test
    void ownerCanEditApprovedReviewAndItReturnsToPending() {
        Review approved = savedReview();
        approved.setStatus("APPROVED");
        approved.setReviewedBy(8L);
        approved.setReviewedAt(LocalDateTime.now().minusDays(1));
        Order completed = order(7L, "COMPLETED");
        Review updated = savedReview();
        updated.setRating(3);
        updated.setContent("后来觉得一般");
        updated.setReviewedBy(null);
        updated.setReviewedAt(null);
        when(reviewMapper.selectOne(any(Wrapper.class))).thenReturn(approved);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(completed);
        when(orderItemMapper.selectOne(any(Wrapper.class))).thenReturn(item(51L, 35L, 9L));
        when(reviewMapper.updateOwnReview(90L, 7L, 3, "后来觉得一般")).thenReturn(1);
        when(reviewMapper.selectById(90L)).thenReturn(updated);

        var result = reviewService.update(7L, 90L, new ReviewUpdateRequest(3, "后来觉得一般"));

        assertEquals(3, result.rating());
        assertEquals("PENDING", result.status());
        assertNull(result.reviewedAt());
        verify(reviewMapper).updateOwnReview(90L, 7L, 3, "后来觉得一般");
    }

    @Test
    void hiddenReviewCanAlsoBeEditedWithinTheWindow() {
        Review hidden = savedReview();
        hidden.setStatus("HIDDEN");
        Review updated = savedReview();
        updated.setRating(2);
        updated.setStatus("PENDING");
        when(reviewMapper.selectOne(any(Wrapper.class))).thenReturn(hidden);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "COMPLETED"));
        when(orderItemMapper.selectOne(any(Wrapper.class))).thenReturn(item(51L, 35L, 9L));
        when(reviewMapper.updateOwnReview(90L, 7L, 2, "重写")).thenReturn(1);
        when(reviewMapper.selectById(90L)).thenReturn(updated);

        var result = reviewService.update(7L, 90L, new ReviewUpdateRequest(2, "重写"));

        assertEquals("PENDING", result.status());
        verify(reviewMapper).updateOwnReview(90L, 7L, 2, "重写");
    }

    @Test
    void studentCannotEditAnotherUsersReview() {
        when(reviewMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> reviewService.update(7L, 90L, new ReviewUpdateRequest(4, "内容")));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(orderMapper, orderItemMapper);
        verify(reviewMapper, never()).updateOwnReview(any(), any(), any(), any());
    }

    @Test
    void incompleteOrExpiredOrderReviewCannotBeEdited() {
        Review review = savedReview();
        when(reviewMapper.selectOne(any(Wrapper.class))).thenReturn(review);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "READY"));
        BusinessException incomplete = assertThrows(BusinessException.class,
                () -> reviewService.update(7L, 90L, new ReviewUpdateRequest(4, "内容")));
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED, incomplete.getErrorCode());
        verifyNoInteractions(orderItemMapper);

        Order expired = order(7L, "COMPLETED");
        expired.setCompletedAt(LocalDateTime.now().minusDays(31));
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(expired);
        BusinessException old = assertThrows(BusinessException.class,
                () -> reviewService.update(7L, 90L, new ReviewUpdateRequest(4, "内容")));
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED, old.getErrorCode());
        verify(reviewMapper, never()).updateOwnReview(any(), any(), any(), any());
    }

    @Test
    void incompleteOrderCannotBeReviewed() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "READY"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> reviewService.create(7L, request()));
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED, error.getErrorCode());
        verifyNoInteractions(orderItemMapper, reviewMapper);
    }

    @Test
    void completedOrderOutsideDocumentedReviewWindowCannotBeReviewed() {
        Order oldCompletedOrder = order(7L, "COMPLETED");
        oldCompletedOrder.setCompletedAt(LocalDateTime.now().minusDays(31));
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(oldCompletedOrder);
        BusinessException error = assertThrows(BusinessException.class,
                () -> reviewService.create(7L, request()));
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED, error.getErrorCode());
        verifyNoInteractions(orderItemMapper, reviewMapper);
    }

    @Test
    void orderItemMustBelongToOrderAndRequestedDish() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "COMPLETED"));
        when(orderItemMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> reviewService.create(7L, request()));
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED, error.getErrorCode());
        verifyNoInteractions(reviewMapper);

        when(orderItemMapper.selectOne(any(Wrapper.class))).thenReturn(item(51L, 35L, 10L));
        BusinessException wrongDish = assertThrows(BusinessException.class,
                () -> reviewService.create(7L, request()));
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED, wrongDish.getErrorCode());
        verifyNoInteractions(reviewMapper);
    }

    @Test
    void duplicateReviewIsRejectedByPrecheckAndUniqueConstraintFallback() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "COMPLETED"));
        when(orderItemMapper.selectOne(any(Wrapper.class))).thenReturn(item(51L, 35L, 9L));
        when(reviewMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        BusinessException existing = assertThrows(BusinessException.class,
                () -> reviewService.create(7L, request()));
        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, existing.getErrorCode());
        verify(reviewMapper, never()).insert(any(Review.class));

        when(reviewMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(reviewMapper.insert(any(Review.class))).thenThrow(new DuplicateKeyException("unique"));
        BusinessException concurrent = assertThrows(BusinessException.class,
                () -> reviewService.create(7L, request()));
        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, concurrent.getErrorCode());
    }

    @Test
    void myReviewsArePagedAndMapped() {
        Review review = savedReview();
        Page<Review> page = new Page<>(2, 5);
        page.setTotal(6);
        page.setRecords(List.of(review));
        when(reviewMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = reviewService.getMyReviews(7L, new ReviewPageRequest(2, 5));

        assertEquals(6, result.total());
        assertEquals(2, result.page());
        assertEquals(5, result.size());
        assertEquals(90L, result.records().get(0).id());
    }

    @Test
    void publicDishReviewsMapOnlyPublicFields() {
        Review review = savedReview();
        review.setStatus("APPROVED");
        Page<Review> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(review));
        when(reviewMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = reviewService.getApprovedDishReviews(9L, new ReviewPageRequest(null, null));

        assertEquals(1, result.total());
        assertEquals(5, result.records().get(0).rating());
        assertFalse(result.records().get(0).toString().contains("userId"));
    }

    @Test
    void adminCanAuditApprovalAndHideWithAdminId() {
        when(reviewMapper.selectById(90L)).thenReturn(savedReview());
        when(reviewMapper.auditReview(90L, 8L, "APPROVED")).thenReturn(1);
        reviewService.audit(8L, 90L, new ReviewAuditRequest("APPROVED"));
        verify(reviewMapper).auditReview(90L, 8L, "APPROVED");

        when(reviewMapper.auditReview(90L, 9L, "HIDDEN")).thenReturn(1);
        reviewService.audit(9L, 90L, new ReviewAuditRequest("HIDDEN"));
        verify(reviewMapper).auditReview(90L, 9L, "HIDDEN");
    }

    @Test
    void idempotentAuditOfExistingReviewDoesNotLookLikeNotFound() {
        when(reviewMapper.selectById(90L)).thenReturn(savedReview());
        when(reviewMapper.auditReview(90L, 8L, "APPROVED")).thenReturn(0);
        assertDoesNotThrow(() -> reviewService.audit(8L, 90L, new ReviewAuditRequest("APPROVED")));
    }

    @Test
    void adminReviewQuerySupportsStatusFiltering() {
        Page<Review> page = new Page<>(1, 10);
        page.setTotal(0);
        page.setRecords(List.of());
        when(reviewMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = reviewService.getAdminReviews(new AdminReviewQueryRequest(1, 10, "PENDING"));

        assertEquals(0, result.total());
        verify(reviewMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    private ReviewCreateRequest request() {
        return new ReviewCreateRequest(35L, 51L, 9L, 4, "满意");
    }

    private Order order(Long userId, String status) {
        Order order = new Order();
        order.setId(35L);
        order.setUserId(userId);
        order.setStatus(status);
        order.setCompletedAt(LocalDateTime.now().minusDays(1));
        return order;
    }

    private OrderItem item(Long id, Long orderId, Long dishId) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setDishId(dishId);
        return item;
    }

    private Review savedReview() {
        Review review = new Review();
        review.setId(90L);
        review.setUserId(7L);
        review.setDishId(9L);
        review.setOrderId(35L);
        review.setOrderItemId(51L);
        review.setRating(5);
        review.setContent("很好吃");
        review.setStatus("PENDING");
        return review;
    }
}
