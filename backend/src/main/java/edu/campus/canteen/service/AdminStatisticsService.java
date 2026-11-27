package edu.campus.canteen.service;

import edu.campus.canteen.dto.StatisticsDateRangeRequest;
import edu.campus.canteen.vo.AdminStatisticsSummaryVO;
import edu.campus.canteen.vo.AdminDishSalesVO;
import java.util.List;

public interface AdminStatisticsService {
    AdminStatisticsSummaryVO getSummary(StatisticsDateRangeRequest request);

    List<AdminDishSalesVO> getDishSales(StatisticsDateRangeRequest request);
}
