package edu.campus.canteen.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartAddRequest(
        @NotNull(message = "菜品ID不能为空")
        @Min(value = 1, message = "菜品ID必须为正数")
        Long dishId,
        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于零")
        Integer quantity
) {
}
