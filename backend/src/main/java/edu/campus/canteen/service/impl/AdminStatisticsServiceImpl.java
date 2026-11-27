package edu.campus.canteen.service.impl;

import edu.campus.canteen.dto.StatisticsDateRangeRequest;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.mapper.StatisticsSummaryRow;
import edu.campus.canteen.mapper.DishSalesSummaryRow;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.service.AdminStatisticsService;
import edu.campus.canteen.vo.AdminStatisticsSummaryVO;
import edu.campus.canteen.vo.AdminDishSalesVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminStatisticsServiceImpl implements AdminStatisticsService {
    private final OrderMapper orderMapper;
    private final DishMapper dishMapper;

    public AdminStatisticsServiceImpl(OrderMapper orderMapper, DishMapper dishMapper) {
        this.orderMapper = orderMapper;
        this.dishMapper = dishMapper;
    }

    @Override
    public AdminStatisticsSummaryVO getSummary(StatisticsDateRangeRequest request) {
        StatisticsSummaryRow row = orderMapper.selectStatisticsSummary(request.startDate(), request.endDate());
        return new AdminStatisticsSummaryVO(request.startDate(), request.endDate(),
                row == null || row.getTotalOrders() == null ? 0 : row.getTotalOrders(),
                row == null || row.getCompletedOrders() == null ? 0 : row.getCompletedOrders(),
                row == null || row.getCompletedSales() == null ? BigDecimal.ZERO : row.getCompletedSales());
    }

    @Override
    public List<AdminDishSalesVO> getDishSales(StatisticsDateRangeRequest request) {
        return dishMapper.selectDishSalesSummary(request.startDate(), request.endDate()).stream()
                .map(row -> new AdminDishSalesVO(row.getDishId(), row.getDishName(),
                        row.getSalesQuantity() == null ? 0 : row.getSalesQuantity()))
                .toList();
    }
}
