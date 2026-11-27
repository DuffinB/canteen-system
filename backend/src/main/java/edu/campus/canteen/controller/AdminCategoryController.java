package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.CategoryCreateRequest;
import edu.campus.canteen.dto.CategoryStatusRequest;
import edu.campus.canteen.dto.CategoryUpdateRequest;
import edu.campus.canteen.service.CategoryService;
import edu.campus.canteen.vo.AdminCategoryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) { this.categoryService = categoryService; }

    @GetMapping
    public ApiResponse<List<AdminCategoryVO>> list() {
        return ApiResponse.success(categoryService.getAdminCategories());
    }

    @PostMapping
    public ApiResponse<AdminCategoryVO> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.success(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCategoryVO> update(@PathVariable @Positive Long id,
                                                @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.updateCategory(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminCategoryVO> updateStatus(@PathVariable @Positive Long id,
                                                       @Valid @RequestBody CategoryStatusRequest request) {
        return ApiResponse.success(categoryService.updateCategoryStatus(id, request));
    }
}
