package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.StatisticsDateRangeRequest;
import edu.campus.canteen.service.AdminStatisticsService;
import edu.campus.canteen.vo.AdminStatisticsSummaryVO;
import edu.campus.canteen.vo.AdminDishSalesVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {
    private final AdminStatisticsService statisticsService;

    public AdminStatisticsController(AdminStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminStatisticsSummaryVO> summary(@Valid @ModelAttribute StatisticsDateRangeRequest request) {
        return ApiResponse.success(statisticsService.getSummary(request));
    }

    @GetMapping("/dishes")
    public ApiResponse<List<AdminDishSalesVO>> dishSales(@Valid @ModelAttribute StatisticsDateRangeRequest request) {
        return ApiResponse.success(statisticsService.getDishSales(request));
    }
}
