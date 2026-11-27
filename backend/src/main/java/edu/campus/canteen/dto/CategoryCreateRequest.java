package edu.campus.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(@NotBlank @Size(max = 50) String name,
                                    @NotNull @PositiveOrZero Integer sortOrder) {
}
