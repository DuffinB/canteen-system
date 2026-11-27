package edu.campus.canteen.vo;

import java.math.BigDecimal;
import java.util.List;

public record CartVO(List<CartItemVO> items, BigDecimal totalAmount) {
}
