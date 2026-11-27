package edu.campus.canteen.service;

import edu.campus.canteen.dto.StatisticsDateRangeRequest;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.mapper.StatisticsSummaryRow;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.mapper.DishSalesSummaryRow;
import edu.campus.canteen.vo.AdminDishSalesVO;
import edu.campus.canteen.service.impl.AdminStatisticsServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class AdminStatisticsServiceTest {
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final DishMapper dishMapper = mock(DishMapper.class);
    private final AdminStatisticsService service = new AdminStatisticsServiceImpl(orderMapper, dishMapper);

    @Test
    void summaryUsesDatabaseAggregateAndReturnsValues() {
        LocalDate start = LocalDate.parse("2026-01-01");
        LocalDate end = LocalDate.parse("2026-01-31");
        StatisticsSummaryRow row = new StatisticsSummaryRow();
        row.setTotalOrders(8L);
        row.setCompletedOrders(5L);
        row.setCompletedSales(new BigDecimal("123.45"));
        when(orderMapper.selectStatisticsSummary(start, end)).thenReturn(row);

        var result = service.getSummary(new StatisticsDateRangeRequest(start, end));

        assertEquals(8L, result.totalOrders());
        assertEquals(5L, result.completedOrders());
        assertEquals(new BigDecimal("123.45"), result.completedSales());
        verify(orderMapper).selectStatisticsSummary(start, end);
    }

    @Test
    void missingAggregateResultDefaultsToZero() {
        LocalDate date = LocalDate.parse("2026-02-01");
        when(orderMapper.selectStatisticsSummary(date, date)).thenReturn(null);
        var result = service.getSummary(new StatisticsDateRangeRequest(date, date));
        assertEquals(0L, result.totalOrders());
        assertEquals(0L, result.completedOrders());
        assertEquals(BigDecimal.ZERO, result.completedSales());
    }

    @Test
    void dishSalesReturnsDatabaseAggregateRowsInMapperOrder() {
        LocalDate start = LocalDate.parse("2026-04-01");
        LocalDate end = LocalDate.parse("2026-04-30");
        DishSalesSummaryRow row = new DishSalesSummaryRow();
        row.setDishId(12L);
        row.setDishName("宫保鸡丁");
        row.setSalesQuantity(19L);
        when(dishMapper.selectDishSalesSummary(start, end)).thenReturn(java.util.List.of(row));

        var result = service.getDishSales(new StatisticsDateRangeRequest(start, end));

        assertEquals(new AdminDishSalesVO(12L, "宫保鸡丁", 19L), result.get(0));
        verify(dishMapper).selectDishSalesSummary(start, end);
    }

    @Test
    void summarySqlUsesCreatedTimeForCountsAndCompletedTimeForRevenue() throws Exception {
        String sql = OrderMapper.class.getMethod("selectStatisticsSummary", LocalDate.class, LocalDate.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value()[0];
        assertTrue(sql.contains("created_at"));
        assertTrue(sql.contains("completed_at"));
        assertTrue(sql.contains("order_items oi"));
        assertTrue(sql.contains("SUM(oi.subtotal)"));
        assertTrue(sql.contains("DATE_ADD(#{endDate}, INTERVAL 1 DAY)"));
    }

    @Test
    void dishSalesSqlUsesCompletedAtAndQuantity() throws Exception {
        String sql = DishMapper.class.getMethod("selectDishSalesSummary", LocalDate.class, LocalDate.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value()[0];
        assertTrue(sql.contains("o.completed_at"));
        assertTrue(sql.contains("SUM(oi.quantity)"));
        assertTrue(sql.contains("o.status = 'COMPLETED'"));
        assertTrue(sql.contains("ORDER BY SUM(oi.quantity) DESC"));
    }
}
