package edu.campus.canteen.vo;

import java.time.LocalDateTime;

/** 当前学生的评价资料。 */
public record ReviewVO(
        Long id,
        Long orderId,
        Long orderItemId,
        Long dishId,
        Integer rating,
        String content,
        String status,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
}
