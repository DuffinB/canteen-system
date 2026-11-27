package edu.campus.canteen.service;

import edu.campus.canteen.vo.CategoryVO;
import edu.campus.canteen.dto.CategoryCreateRequest;
import edu.campus.canteen.dto.CategoryStatusRequest;
import edu.campus.canteen.dto.CategoryUpdateRequest;
import edu.campus.canteen.vo.AdminCategoryVO;

import java.util.List;

public interface CategoryService {
    List<CategoryVO> getPublicCategories();
    List<AdminCategoryVO> getAdminCategories();
    AdminCategoryVO createCategory(CategoryCreateRequest request);
    AdminCategoryVO updateCategory(Long id, CategoryUpdateRequest request);
    AdminCategoryVO updateCategoryStatus(Long id, CategoryStatusRequest request);
}
