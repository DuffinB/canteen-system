package edu.campus.canteen.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 管理员订单详情，商品字段均来自下单时保存的明细快照。 */
public record AdminOrderDetailVO(
        Long id,
        String orderNo,
        Long studentId,
        String studentNo,
        String studentNickname,
        BigDecimal totalAmount,
        String status,
        LocalDate pickupDate,
        String pickupSlot,
        String remark,
        Long handledBy,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        List<OrderItemVO> items
) {
}
