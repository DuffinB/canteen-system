package edu.campus.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminUserQueryRequest(
        @Min(value = 1, message = "页码至少为1") @Max(value = 100000, message = "页码不能超过100000") Integer page,
        @Min(value = 1, message = "每页数量至少为1") @Max(value = 100, message = "每页最多100条") Integer size
) {
    public int pageOrDefault() { return page == null ? 1 : page; }
    public int sizeOrDefault() { return size == null ? 10 : size; }
}
