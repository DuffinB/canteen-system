package edu.campus.canteen.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminStatisticsSummaryVO(LocalDate startDate, LocalDate endDate,
                                      long totalOrders, long completedOrders,
                                      BigDecimal completedSales) {
}
