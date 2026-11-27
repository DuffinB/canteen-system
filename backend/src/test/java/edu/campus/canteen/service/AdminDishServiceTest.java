package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
class AdminDishServiceTest {
    @Mock private DishMapper dishMapper;
    @Mock private CategoryMapper categoryMapper;
    private DishServiceImpl dishService;

    @BeforeEach
    void setUp() {
        dishService = new DishServiceImpl(dishMapper, categoryMapper);
    }

    @Test
    void createShouldSetInitialStatusOffSale() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, 1));
        when(dishMapper.insert(any(Dish.class))).thenAnswer(invocation -> {
            ((Dish) invocation.getArgument(0)).setId(9L);
            return 1;
        });
        when(dishMapper.selectById(9L)).thenAnswer(invocation -> dishMapperInsertedDish());

        var result = dishService.createDish(new DishCreateRequest(3L, "盖饭", null, null,
                new BigDecimal("16.50"), 8));

        assertEquals("OFF_SALE", result.status());
        assertEquals("盖饭", result.name());
        verify(dishMapper).insert(argThat((Dish dish) -> "OFF_SALE".equals(dish.getStatus())
                && Integer.valueOf(0).equals(dish.getDeleted())));
    }

    @Test
    void createShouldRejectMissingOrDisabledCategory() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dishService.createDish(new DishCreateRequest(3L, "盖饭", null, null,
                        new BigDecimal("16.50"), 8)));

        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, exception.getErrorCode());
        verify(dishMapper, never()).insert(any(Dish.class));
    }

    @Test
    void updateShouldChangeBasicInformationAndValidateCategory() {
        Dish existing = dish(9L, 3L);
        when(categoryMapper.selectById(4L)).thenReturn(category(4L, 1));
        when(dishMapper.selectById(9L)).thenReturn(existing);
        var result = dishService.updateDish(9L, new DishUpdateRequest(4L, "新菜名", "介绍", "image.png"));

        assertEquals("新菜名", result.name());
        assertEquals(4L, result.categoryId());
        verify(dishMapper).updateById(existing);
    }

    @Test
    void publishShouldRequireEnabledCategory() {
        when(dishMapper.selectById(9L)).thenReturn(dish(9L, 3L));
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dishService.updateDishStatus(9L, new DishStatusRequest("ON_SALE")));

        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, exception.getErrorCode());
        verify(dishMapper, never()).updateById(any(Dish.class));
    }

    @Test
    void publishShouldSucceedWhenCategoryIsEnabled() {
        Dish existing = dish(9L, 3L);
        when(dishMapper.selectById(9L)).thenReturn(existing);
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, 1));

        var result = dishService.updateDishStatus(9L, new DishStatusRequest("ON_SALE"));

        assertEquals("ON_SALE", result.status());
        verify(dishMapper).updateById(existing);
    }

    @Test
    void adminListShouldIncludeOffSaleDish() {
        Dish offSale = dish(9L, 3L);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Dish>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(offSale));
        when(dishMapper.selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any(Wrapper.class)))
                .thenReturn(page);
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(category(3L, 0)));

        var result = dishService.getAdminDishes(new DishQueryRequest(null, null, null, null, null, null));

        assertEquals(1, result.total());
        assertEquals("OFF_SALE", result.records().get(0).status());
        verify(dishMapper).selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any(Wrapper.class));
    }

    @Test
    void updatePriceAndStockShouldPersistValues() {
        Dish existing = dish(9L, 3L);
        when(dishMapper.selectById(9L)).thenReturn(existing);
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, 1));

        dishService.updateDishPrice(9L, new DishPriceRequest(new BigDecimal("21.25")));
        assertEquals(new BigDecimal("21.25"), existing.getPrice());
        dishService.updateDishStock(9L, new DishStockRequest(0));
        assertEquals(0, existing.getStock());
        verify(dishMapper, times(2)).updateById(existing);
    }

    @Test
    void deleteShouldUseMapperLogicDeleteOperation() {
        when(dishMapper.selectById(9L)).thenReturn(dish(9L, 3L));

        dishService.deleteDish(9L);

        verify(dishMapper).deleteById(9L);
    }

    private Dish dishMapperInsertedDish() {
        var captor = org.mockito.ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).insert(captor.capture());
        return captor.getValue();
    }

    private Category category(Long id, int status) {
        Category category = new Category();
        category.setId(id);
        category.setName("分类" + id);
        category.setStatus(status);
        category.setDeleted(0);
        return category;
    }

    private Dish dish(Long id, Long categoryId) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setCategoryId(categoryId);
        dish.setName("菜品");
        dish.setStatus("OFF_SALE");
        dish.setPrice(new BigDecimal("10.00"));
        dish.setStock(5);
        dish.setDeleted(0);
        return dish;
    }
}
