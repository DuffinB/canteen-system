package edu.campus.canteen.mapper;

import java.math.BigDecimal;

/** 数据库聚合查询结果。 */
public class StatisticsSummaryRow {
    private Long totalOrders;
    private Long completedOrders;
    private BigDecimal completedSales;
    public Long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Long totalOrders) { this.totalOrders = totalOrders; }
    public Long getCompletedOrders() { return completedOrders; }
    public void setCompletedOrders(Long completedOrders) { this.completedOrders = completedOrders; }
    public BigDecimal getCompletedSales() { return completedSales; }
    public void setCompletedSales(BigDecimal completedSales) { this.completedSales = completedSales; }
}
