package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.dto.CategoryCreateRequest;
import edu.campus.canteen.dto.CategoryStatusRequest;
import edu.campus.canteen.dto.CategoryUpdateRequest;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.service.CategoryService;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.vo.AdminCategoryVO;
import edu.campus.canteen.vo.CategoryVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryVO> getPublicCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSortOrder)
                        .orderByAsc(Category::getId))
                .stream()
                .map(category -> new CategoryVO(category.getId(), category.getName(), category.getSortOrder()))
                .toList();
    }

    @Override
    public List<AdminCategoryVO> getAdminCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId))
                .stream().map(this::toAdminVO).toList();
    }

    @Override
    @Transactional
    public AdminCategoryVO createCategory(CategoryCreateRequest request) {
        Category category = new Category();
        category.setName(request.name().trim());
        category.setSortOrder(request.sortOrder());
        category.setStatus(1);
        category.setDeleted(0);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
        return toAdminVO(categoryMapper.selectById(category.getId()));
    }

    @Override
    @Transactional
    public AdminCategoryVO updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = requireCategory(id);
        category.setName(request.name().trim());
        category.setSortOrder(request.sortOrder());
        try {
            categoryMapper.updateById(category);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
        return toAdminVO(categoryMapper.selectById(id));
    }

    @Override
    @Transactional
    public AdminCategoryVO updateCategoryStatus(Long id, CategoryStatusRequest request) {
        Category category = requireCategory(id);
        category.setStatus(request.status());
        categoryMapper.updateById(category);
        return toAdminVO(categoryMapper.selectById(id));
    }

    private Category requireCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return category;
    }

    private AdminCategoryVO toAdminVO(Category category) {
        return new AdminCategoryVO(category.getId(), category.getName(), category.getSortOrder(),
                category.getStatus(), category.getCreatedAt(), category.getUpdatedAt());
    }
}
