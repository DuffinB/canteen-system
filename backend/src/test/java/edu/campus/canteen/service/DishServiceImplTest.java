package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.campus.canteen.dto.DishQueryRequest;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.Dish;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.service.impl.DishServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishServiceImplTest {
    @Mock
    private DishMapper dishMapper;
    @Mock
    private CategoryMapper categoryMapper;

    private DishServiceImpl dishService;

    @BeforeEach
    void setUp() {
        dishService = new DishServiceImpl(dishMapper, categoryMapper);
    }

    @Test
    void publicListShouldReturnPagedDishesWithCategoryName() {
        Category category = category(3L, "面食", 1);
        Dish dish = dish(8L, 3L, "牛肉面", "ON_SALE");
        Page<Dish> page = new Page<>(2, 5);
        page.setTotal(6);
        page.setRecords(List.of(dish));
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(category));
        when(dishMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = dishService.getPublicDishes(new DishQueryRequest(2, 5, 3L, "牛肉", "priceAsc", null));

        assertEquals(6, result.total());
        assertEquals(2, result.page());
        assertEquals(5, result.size());
        assertEquals("面食", result.records().get(0).categoryName());
        assertEquals(new BigDecimal("18.00"), result.records().get(0).price());
        verify(dishMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void publicListShouldNotQueryDishesWhenNoActiveCategoryExists() {
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var result = dishService.getPublicDishes(new DishQueryRequest(null, null, null, null, null, null));

        assertTrue(result.records().isEmpty());
        assertEquals(0, result.total());
        assertEquals(1, result.page());
        assertEquals(10, result.size());
        verifyNoInteractions(dishMapper);
    }

    @Test
    void publicDetailShouldRejectDishThatIsNotOnSale() {
        when(dishMapper.selectById(8L)).thenReturn(dish(8L, 3L, "牛肉面", "OFF_SALE"));

        BusinessException exception = assertThrows(BusinessException.class, () -> dishService.getPublicDishById(8L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(categoryMapper);
    }

    @Test
    void publicDetailShouldRejectDishWithDisabledCategory() {
        when(dishMapper.selectById(8L)).thenReturn(dish(8L, 3L, "牛肉面", "ON_SALE"));
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食", 0));

        BusinessException exception = assertThrows(BusinessException.class, () -> dishService.getPublicDishById(8L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    private Category category(Long id, String name, int status) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setStatus(status);
        category.setDeleted(0);
        return category;
    }

    private Dish dish(Long id, Long categoryId, String name, String status) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setCategoryId(categoryId);
        dish.setName(name);
        dish.setStatus(status);
        dish.setPrice(new BigDecimal("18.00"));
        dish.setStock(12);
        dish.setDeleted(0);
        return dish;
    }
}
