package edu.campus.canteen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DishCreateRequest(
        @NotNull(message = "分类不能为空") @Positive(message = "分类ID必须为正数") Long categoryId,
        @NotBlank(message = "菜品名称不能为空") @Size(max = 100, message = "菜品名称不能超过100个字符") String name,
        @Size(max = 1000, message = "菜品介绍不能超过1000个字符") String description,
        @Size(max = 500, message = "图片地址不能超过500个字符") String imageUrl,
        @NotNull(message = "价格不能为空") @DecimalMin(value = "0.00", message = "价格不能小于0")
        @Digits(integer = 8, fraction = 2, message = "价格最多8位整数且最多2位小数") BigDecimal price,
        @NotNull(message = "库存不能为空") @Min(value = 0, message = "库存不能小于0") Integer stock
) {
}
