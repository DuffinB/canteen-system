package edu.campus.canteen.vo;

import java.time.LocalDateTime;

/** 管理员评价列表资料。 */
public record AdminReviewVO(
        Long id,
        Long userId,
        Long dishId,
        Long orderId,
        Long orderItemId,
        Integer rating,
        String content,
        String status,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
}
