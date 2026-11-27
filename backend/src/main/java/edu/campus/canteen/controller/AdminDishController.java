package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.DishCreateRequest;
import edu.campus.canteen.dto.DishPriceRequest;
import edu.campus.canteen.dto.DishQueryRequest;
import edu.campus.canteen.dto.DishStatusRequest;
import edu.campus.canteen.dto.DishStockRequest;
import edu.campus.canteen.dto.DishUpdateRequest;
import edu.campus.canteen.service.DishService;
import edu.campus.canteen.vo.DishVO;
import edu.campus.canteen.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/dishes")
public class AdminDishController {
    private final DishService dishService;

    public AdminDishController(DishService dishService) {
        this.dishService = dishService;
    }

    @PostMapping
    public ApiResponse<DishVO> create(@Valid @RequestBody DishCreateRequest request) {
        return ApiResponse.success(dishService.createDish(request));
    }

    @GetMapping
    public ApiResponse<PageVO<DishVO>> list(@Valid @ModelAttribute DishQueryRequest query) {
        return ApiResponse.success(dishService.getAdminDishes(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<DishVO> get(@PathVariable @Positive Long id) {
        return ApiResponse.success(dishService.getAdminDishById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DishVO> update(@PathVariable @Positive Long id,
                                      @Valid @RequestBody DishUpdateRequest request) {
        return ApiResponse.success(dishService.updateDish(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DishVO> updateStatus(@PathVariable @Positive Long id,
                                            @Valid @RequestBody DishStatusRequest request) {
        return ApiResponse.success(dishService.updateDishStatus(id, request));
    }

    @PatchMapping("/{id}/stock")
    public ApiResponse<DishVO> updateStock(@PathVariable @Positive Long id,
                                           @Valid @RequestBody DishStockRequest request) {
        return ApiResponse.success(dishService.updateDishStock(id, request));
    }

    @PatchMapping("/{id}/price")
    public ApiResponse<DishVO> updatePrice(@PathVariable @Positive Long id,
                                           @Valid @RequestBody DishPriceRequest request) {
        return ApiResponse.success(dishService.updateDishPrice(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        dishService.deleteDish(id);
        return ApiResponse.success(null);
    }
}
