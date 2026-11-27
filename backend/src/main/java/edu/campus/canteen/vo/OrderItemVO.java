package edu.campus.canteen.vo;

import java.math.BigDecimal;

public record OrderItemVO(
        Long dishId,
        String dishName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {
}
