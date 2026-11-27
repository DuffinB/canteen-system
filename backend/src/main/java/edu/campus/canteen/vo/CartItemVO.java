package edu.campus.canteen.vo;

import java.math.BigDecimal;

public record CartItemVO(
        Long dishId,
        String name,
        String imageUrl,
        BigDecimal price,
        Integer quantity,
        BigDecimal subtotal
) {
}
