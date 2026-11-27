package edu.campus.canteen.vo;

import java.time.LocalDateTime;

/** 公开评价不返回用户标识或审核信息。 */
public record DishReviewVO(Long id, Integer rating, String content, LocalDateTime createdAt) {
}
