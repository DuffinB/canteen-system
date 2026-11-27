package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.service.CategoryService;
import edu.campus.canteen.vo.CategoryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryVO>> listPublicCategories() {
        return ApiResponse.success(categoryService.getPublicCategories());
    }
}
