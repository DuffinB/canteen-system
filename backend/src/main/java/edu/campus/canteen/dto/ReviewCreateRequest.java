package edu.campus.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive Long orderItemId,
        @NotNull @Positive Long dishId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String content
) {
}
