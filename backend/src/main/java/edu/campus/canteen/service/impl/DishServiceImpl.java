package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.campus.canteen.dto.DishCreateRequest;
import edu.campus.canteen.dto.DishPriceRequest;
import edu.campus.canteen.dto.DishQueryRequest;
import edu.campus.canteen.dto.DishStatusRequest;
import edu.campus.canteen.dto.DishStockRequest;
import edu.campus.canteen.dto.DishUpdateRequest;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.Dish;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.service.DishService;
import edu.campus.canteen.vo.DishVO;
import edu.campus.canteen.vo.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DishServiceImpl implements DishService {
    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;

    public DishServiceImpl(DishMapper dishMapper, CategoryMapper categoryMapper) {
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public PageVO<DishVO> getPublicDishes(DishQueryRequest query) {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1));
        List<Long> categoryIds = categories.stream().map(Category::getId).toList();
        if (query.categoryId() != null) {
            categoryIds = categories.stream()
                    .map(Category::getId)
                    .filter(id -> id.equals(query.categoryId()))
                    .toList();
        }
        if (categoryIds.isEmpty()) {
            return new PageVO<>(List.of(), 0, query.pageOrDefault(), query.sizeOrDefault());
        }

        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, "ON_SALE")
                .in(Dish::getCategoryId, categoryIds)
                .like(query.keyword() != null && !query.keyword().isBlank(), Dish::getName,
                        query.keyword() == null ? null : query.keyword().trim());
        applyPublicSort(wrapper, query.sort());

        Page<Dish> result = dishMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()), wrapper);
        Map<Long, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        List<DishVO> records = result.getRecords().stream()
                .map(dish -> toDishVO(dish, categoryNames.get(dish.getCategoryId())))
                .toList();
        return new PageVO<>(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public DishVO getPublicDishById(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null || !"ON_SALE".equals(dish.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Category category = categoryMapper.selectById(dish.getCategoryId());
        if (category == null || !Integer.valueOf(1).equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toDishVO(dish, category.getName());
    }

    @Override
    public List<DishVO> getHotDishes() {
        List<Dish> dishes = dishMapper.selectTopSellingDishes();
        if (dishes.isEmpty()) return List.of();
        Map<Long, String> categoryNames = loadCategoryNames(dishes);
        return dishes.stream().map(dish -> toDishVO(dish, categoryNames.get(dish.getCategoryId()))).toList();
    }

    @Override
    public PageVO<DishVO> getAdminDishes(DishQueryRequest query) {
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .eq(query.categoryId() != null, Dish::getCategoryId, query.categoryId())
                .like(query.keyword() != null && !query.keyword().isBlank(), Dish::getName,
                        query.keyword() == null ? null : query.keyword().trim())
                .eq(query.status() != null && !query.status().isBlank(), Dish::getStatus, query.status());
        applyPublicSort(wrapper, query.sort());

        Page<Dish> result = dishMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()), wrapper);
        Map<Long, String> categoryNames = loadCategoryNames(result.getRecords());
        List<DishVO> records = result.getRecords().stream()
                .map(dish -> toDishVO(dish, categoryNames.get(dish.getCategoryId())))
                .toList();
        return new PageVO<>(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public DishVO getAdminDishById(Long id) {
        Dish dish = requireDish(id);
        Category category = categoryMapper.selectById(dish.getCategoryId());
        return toDishVO(dish, category == null ? null : category.getName());
    }

    @Override
    @Transactional
    public DishVO createDish(DishCreateRequest request) {
        requireEnabledCategory(request.categoryId());
        Dish dish = new Dish();
        dish.setCategoryId(request.categoryId());
        dish.setName(request.name().trim());
        dish.setDescription(request.description());
        dish.setImageUrl(request.imageUrl());
        dish.setPrice(request.price());
        dish.setStock(request.stock());
        dish.setStatus("OFF_SALE");
        dish.setDeleted(0);
        dishMapper.insert(dish);
        return getAdminDishById(dish.getId());
    }

    @Override
    @Transactional
    public DishVO updateDish(Long id, DishUpdateRequest request) {
        requireEnabledCategory(request.categoryId());
        Dish dish = requireDish(id);
        dish.setCategoryId(request.categoryId());
        dish.setName(request.name().trim());
        dish.setDescription(request.description());
        dish.setImageUrl(request.imageUrl());
        dishMapper.updateById(dish);
        return getAdminDishById(id);
    }

    @Override
    @Transactional
    public DishVO updateDishStatus(Long id, DishStatusRequest request) {
        Dish dish = requireDish(id);
        if ("ON_SALE".equals(request.status())) {
            requireEnabledCategory(dish.getCategoryId());
        } else if (!"OFF_SALE".equals(request.status())) {
            throw new BusinessException(ErrorCode.INVALID_DISH_STATUS);
        }
        dish.setStatus(request.status());
        dishMapper.updateById(dish);
        return getAdminDishById(id);
    }

    @Override
    @Transactional
    public DishVO updateDishStock(Long id, DishStockRequest request) {
        Dish dish = requireDish(id);
        dish.setStock(request.stock());
        dishMapper.updateById(dish);
        return getAdminDishById(id);
    }

    @Override
    @Transactional
    public DishVO updateDishPrice(Long id, DishPriceRequest request) {
        Dish dish = requireDish(id);
        dish.setPrice(request.price());
        dishMapper.updateById(dish);
        return getAdminDishById(id);
    }

    @Override
    @Transactional
    public void deleteDish(Long id) {
        requireDish(id);
        // deleteById 遵循 Dish 上的 @TableLogic，只更新 deleted，不执行物理删除。
        dishMapper.deleteById(id);
    }

    private Dish requireDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return dish;
    }

    private Category requireEnabledCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || !Integer.valueOf(1).equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.CATEGORY_UNAVAILABLE);
        }
        return category;
    }

    private Map<Long, String> loadCategoryNames(List<Dish> dishes) {
        List<Long> categoryIds = dishes.stream().map(Dish::getCategoryId).distinct().toList();
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>().in(Category::getId, categoryIds))
                .stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private void applyPublicSort(LambdaQueryWrapper<Dish> wrapper, String sort) {
        // 只使用 DTO 白名单中定义的排序选项，不把客户端值作为 SQL 列名。
        if (sort == null || sort.isBlank() || "latest".equals(sort)) {
            wrapper.orderByDesc(Dish::getUpdatedAt).orderByDesc(Dish::getId);
        } else if ("priceAsc".equals(sort)) {
            wrapper.orderByAsc(Dish::getPrice).orderByAsc(Dish::getId);
        } else if ("priceDesc".equals(sort)) {
            wrapper.orderByDesc(Dish::getPrice).orderByDesc(Dish::getId);
        }
    }

    private DishVO toDishVO(Dish dish, String categoryName) {
        return new DishVO(dish.getId(), dish.getCategoryId(), categoryName, dish.getName(),
                dish.getDescription(), dish.getImageUrl(), dish.getPrice(), dish.getStock(),
                dish.getStatus(), dish.getCreatedAt(), dish.getUpdatedAt());
    }
}
