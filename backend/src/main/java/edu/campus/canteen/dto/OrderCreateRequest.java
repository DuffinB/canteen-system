package edu.campus.canteen.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OrderCreateRequest(
        @NotNull(message = "预约取餐日期不能为空")
        @FutureOrPresent(message = "预约取餐日期不能早于今天")
        LocalDate pickupDate,
        @NotBlank(message = "预约取餐时段不能为空")
        @Pattern(regexp = "^(LUNCH|DINNER)$", message = "预约取餐时段仅支持 LUNCH 或 DINNER")
        String pickupSlot,
        @Size(max = 300, message = "备注不能超过300个字符")
        String remark
) {
}
