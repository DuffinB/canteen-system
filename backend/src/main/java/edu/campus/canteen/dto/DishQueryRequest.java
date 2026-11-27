package edu.campus.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 菜品分页筛选参数；排序只允许预定义选项，不接受 SQL 字段名。 */
public record DishQueryRequest(
        @Min(value = 1, message = "页码至少为1") @Max(value = 100000, message = "页码不能超过100000") Integer page,
        @Min(value = 1, message = "每页数量至少为1") @Max(value = 100, message = "每页最多100条") Integer size,
        @Positive(message = "分类ID必须为正数") Long categoryId,
        @Size(max = 100, message = "搜索关键词不能超过100个字符") String keyword,
        @Pattern(regexp = "^(priceAsc|priceDesc|latest)?$", message = "排序方式不合法") String sort,
        @Pattern(regexp = "^(ON_SALE|OFF_SALE)?$", message = "菜品状态不合法") String status
) {
    public int pageOrDefault() { return page == null ? 1 : page; }
    public int sizeOrDefault() { return size == null ? 10 : size; }
}
