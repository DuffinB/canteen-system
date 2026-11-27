package edu.campus.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewAuditRequest(
        @NotBlank
        @Pattern(regexp = "APPROVED|HIDDEN", message = "审核结果只能为 APPROVED 或 HIDDEN")
        String status
) {
}
