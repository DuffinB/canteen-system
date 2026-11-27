package edu.campus.canteen.service;

import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.service.impl.DishServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;

class HotDishServiceTest {
    @Test
    void noCompletedSalesReturnsEmptyList() {
        DishMapper dishMapper = mock(DishMapper.class);
        when(dishMapper.selectTopSellingDishes()).thenReturn(List.of());
        var result = new DishServiceImpl(dishMapper, mock(CategoryMapper.class)).getHotDishes();
        assertTrue(result.isEmpty());
        verify(dishMapper).selectTopSellingDishes();
    }

    @Test
    void mapperQueryUsesOnlyCompletedSalesAndVisibleDishes() throws Exception {
        String sql = edu.campus.canteen.mapper.DishMapper.class
                .getMethod("selectTopSellingDishes")
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value()[0];
        assertTrue(sql.contains("o.status = 'COMPLETED'"));
        assertTrue(sql.contains("d.status = 'ON_SALE'"));
        assertTrue(sql.contains("d.deleted = 0"));
        assertTrue(sql.contains("SUM(oi.quantity) DESC"));
        assertTrue(sql.contains("LIMIT 10"));
    }
}
