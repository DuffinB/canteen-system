package edu.campus.canteen.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record StatisticsDateRangeRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    @AssertTrue(message = "结束日期不能早于开始日期")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
