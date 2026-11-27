package edu.campus.canteen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DishPriceRequest(
        @NotNull(message = "价格不能为空") @DecimalMin(value = "0.00", message = "价格不能小于0")
        @Digits(integer = 8, fraction = 2, message = "价格最多8位整数且最多2位小数") BigDecimal price
) {
}
