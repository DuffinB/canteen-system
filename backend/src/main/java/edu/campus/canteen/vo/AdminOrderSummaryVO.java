package edu.campus.canteen.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 管理员订单列表摘要，不包含密码哈希等认证资料。 */
public record AdminOrderSummaryVO(
        Long id,
        String orderNo,
        Long studentId,
        String studentNo,
        String studentNickname,
        BigDecimal totalAmount,
        String status,
        LocalDate pickupDate,
        String pickupSlot,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
