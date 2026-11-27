package edu.campus.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DishStatusRequest(
        @NotBlank(message = "菜品状态不能为空")
        @Pattern(regexp = "^(ON_SALE|OFF_SALE)$", message = "菜品状态只能为ON_SALE或OFF_SALE")
        String status
) {
}
