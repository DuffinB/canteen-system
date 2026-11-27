package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.DishQueryRequest;
import edu.campus.canteen.service.DishService;
import edu.campus.canteen.vo.DishVO;
import edu.campus.canteen.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/dishes")
public class DishController {
    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    @GetMapping
    public ApiResponse<PageVO<DishVO>> listPublicDishes(@Valid @ModelAttribute DishQueryRequest query) {
        return ApiResponse.success(dishService.getPublicDishes(query));
    }

    @GetMapping("/hot")
    public ApiResponse<List<DishVO>> listHotDishes() {
        return ApiResponse.success(dishService.getHotDishes());
    }

    @GetMapping("/{id}")
    public ApiResponse<DishVO> getPublicDish(@PathVariable @Positive Long id) {
        return ApiResponse.success(dishService.getPublicDishById(id));
    }
}
