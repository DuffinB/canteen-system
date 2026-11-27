package edu.campus.canteen.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OrderVO(
        Long id,
        String orderNo,
        BigDecimal totalAmount,
        String status,
        LocalDate pickupDate,
        String pickupSlot,
        String remark,
        List<OrderItemVO> items,
        LocalDateTime createdAt
) {
}
