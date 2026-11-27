package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import edu.campus.canteen.service.ReviewService;
import edu.campus.canteen.vo.AdminReviewVO;
import edu.campus.canteen.vo.DishReviewVO;
import edu.campus.canteen.vo.PageVO;
import edu.campus.canteen.vo.ReviewVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String HIDDEN = "HIDDEN";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public ReviewServiceImpl(ReviewMapper reviewMapper, OrderMapper orderMapper,
                             OrderItemMapper orderItemMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    @Transactional
    public ReviewVO create(Long userId, ReviewCreateRequest request) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, request.orderId())
                .eq(Order::getUserId, userId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"COMPLETED".equals(order.getStatus()) || order.getCompletedAt() == null
                || order.getCompletedAt().isBefore(LocalDateTime.now(BUSINESS_ZONE).minusDays(30))) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }

        OrderItem item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getId, request.orderItemId())
                .eq(OrderItem::getOrderId, order.getId())
                .last("FOR UPDATE"));
        if (item == null || !item.getDishId().equals(request.dishId())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderItemId, item.getId())) > 0) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setDishId(item.getDishId());
        review.setOrderId(order.getId());
        review.setOrderItemId(item.getId());
        review.setRating(request.rating());
        review.setContent(request.content());
        review.setStatus(PENDING);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException exception) {
            // 唯一键 uk_reviews_order_item 是并发重复评价的最终保护。
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        return toReviewVO(reviewMapper.selectById(review.getId()));
    }

    @Override
    @Transactional
    public ReviewVO update(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getId, reviewId)
                .eq(Review::getUserId, userId)
                .last("FOR UPDATE"));
        if (review == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, review.getOrderId())
                .eq(Order::getUserId, userId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"COMPLETED".equals(order.getStatus()) || order.getCompletedAt() == null
                || order.getCompletedAt().isBefore(LocalDateTime.now(BUSINESS_ZONE).minusDays(30))) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        OrderItem item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getId, review.getOrderItemId())
                .eq(OrderItem::getOrderId, order.getId()));
        if (item == null || !item.getDishId().equals(review.getDishId())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        int updated = reviewMapper.updateOwnReview(reviewId, userId, request.rating(), request.content());
        if (updated != 1 && reviewMapper.selectById(reviewId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toReviewVO(reviewMapper.selectById(reviewId));
    }

    @Override
    public PageVO<ReviewVO> getMyReviews(Long userId, ReviewPageRequest query) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreatedAt)
                        .orderByDesc(Review::getId));
        return new PageVO<>(page.getRecords().stream().map(this::toReviewVO).toList(),
                page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public PageVO<DishReviewVO> getApprovedDishReviews(Long dishId, ReviewPageRequest query) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getDishId, dishId)
                        .eq(Review::getStatus, APPROVED)
                        .orderByDesc(Review::getCreatedAt)
                        .orderByDesc(Review::getId));
        List<DishReviewVO> records = page.getRecords().stream()
                .map(review -> new DishReviewVO(review.getId(), review.getRating(),
                        review.getContent(), review.getCreatedAt()))
                .toList();
        return new PageVO<>(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public PageVO<AdminReviewVO> getAdminReviews(AdminReviewQueryRequest query) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()),
                new LambdaQueryWrapper<Review>()
                        .eq(query.status() != null, Review::getStatus, query.status())
                        .orderByDesc(Review::getCreatedAt)
                        .orderByDesc(Review::getId));
        return new PageVO<>(page.getRecords().stream().map(this::toAdminReviewVO).toList(),
                page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public void audit(Long adminId, Long reviewId, ReviewAuditRequest request) {
        if (reviewMapper.selectById(reviewId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String status = request.status();
        if (!APPROVED.equals(status) && !HIDDEN.equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int updated = reviewMapper.auditReview(reviewId, adminId, status);
        // MySQL 对值未变化的 UPDATE 可能返回 0；仍存在的评价视作重复审核成功。
        if (updated != 1 && reviewMapper.selectById(reviewId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private ReviewVO toReviewVO(Review review) {
        return new ReviewVO(review.getId(), review.getOrderId(), review.getOrderItemId(), review.getDishId(),
                review.getRating(), review.getContent(), review.getStatus(), review.getReviewedAt(),
                review.getCreatedAt());
    }

    private AdminReviewVO toAdminReviewVO(Review review) {
        return new AdminReviewVO(review.getId(), review.getUserId(), review.getDishId(), review.getOrderId(),
                review.getOrderItemId(), review.getRating(), review.getContent(), review.getStatus(),
                review.getReviewedBy(), review.getReviewedAt(), review.getCreatedAt());
    }
}
