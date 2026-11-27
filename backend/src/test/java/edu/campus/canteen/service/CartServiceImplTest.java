package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.dto.CartAddRequest;
import edu.campus.canteen.dto.CartQuantityRequest;
import edu.campus.canteen.entity.Cart;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.Dish;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.CartMapper;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {
    @Mock private CartMapper cartMapper;
    @Mock private DishMapper dishMapper;
    @Mock private CategoryMapper categoryMapper;
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartMapper, dishMapper, categoryMapper);
    }

    @Test
    void getCartShouldReturnEmptyCart() {
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var result = cartService.getCurrentUserCart(12L);

        assertTrue(result.items().isEmpty());
        assertEquals(new BigDecimal("0.00"), result.totalAmount());
    }

    @Test
    void getCartShouldCalculateSubtotalAndHideMissingOrLogicallyDeletedDishes() {
        Cart liveRow = cart(4L, 12L, 9L, 2);
        Cart missingDishRow = cart(5L, 12L, 10L, 1);
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(liveRow, missingDishRow));
        when(dishMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dish(9L, "ON_SALE", 8, "12.50")));

        var result = cartService.getCurrentUserCart(12L);

        assertEquals(1, result.items().size());
        assertEquals(9L, result.items().get(0).dishId());
        assertEquals(new BigDecimal("25.00"), result.items().get(0).subtotal());
        assertEquals(new BigDecimal("25.00"), result.totalAmount());
        verify(cartMapper).selectList(any(Wrapper.class));
    }

    @Test
    void addShouldInsertStudentCartItem() {
        Dish dish = dish(9L, "ON_SALE", 5, "12.50");
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(category(3L, 1));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(cartMapper.insert(any(Cart.class))).thenAnswer(invocation -> {
            Cart inserted = invocation.getArgument(0);
            inserted.setId(4L);
            return 1;
        });
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cart(4L, 12L, 9L, 2)));
        when(dishMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dish));

        var result = cartService.addItem(12L, new CartAddRequest(9L, 2));

        verify(cartMapper).insert(argThat((Cart item) -> item.getUserId().equals(12L)
                && item.getDishId().equals(9L) && item.getQuantity().equals(2)));
        assertEquals(new BigDecimal("25.00"), result.totalAmount());
    }

    @Test
    void addSameDishShouldAccumulateQuantity() {
        Dish dish = dish(9L, "ON_SALE", 5, "12.50");
        Cart existing = cart(4L, 12L, 9L, 2);
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(category(3L, 1));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        when(dishMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dish));

        cartService.addItem(12L, new CartAddRequest(9L, 2));

        assertEquals(4, existing.getQuantity());
        verify(cartMapper).updateById(existing);
        verify(cartMapper, never()).insert(any(Cart.class));
    }

    @Test
    void addShouldRejectNonPositiveQuantity() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.addItem(12L, new CartAddRequest(9L, 0)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(dishMapper, categoryMapper, cartMapper);
    }

    @Test
    void addShouldRejectDishThatIsNotOnSale() {
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish(9L, "OFF_SALE", 5, "12.50"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.addItem(12L, new CartAddRequest(9L, 1)));

        assertEquals(ErrorCode.INVALID_DISH_STATUS, exception.getErrorCode());
        verifyNoInteractions(categoryMapper, cartMapper);
    }

    @Test
    void addShouldRejectMissingOrLogicallyDeletedDish() {
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.addItem(12L, new CartAddRequest(9L, 1)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(categoryMapper, cartMapper);
    }

    @Test
    void addShouldRejectInsufficientStockIncludingAccumulatedQuantity() {
        Dish dish = dish(9L, "ON_SALE", 3, "12.50");
        Cart existing = cart(4L, 12L, 9L, 2);
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(category(3L, 1));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.addItem(12L, new CartAddRequest(9L, 2)));

        assertEquals(ErrorCode.CART_STOCK_INSUFFICIENT, exception.getErrorCode());
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    void addShouldRejectDishInDisabledCategory() {
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish(9L, "ON_SALE", 5, "12.50"));
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(category(3L, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.addItem(12L, new CartAddRequest(9L, 1)));

        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, exception.getErrorCode());
        verifyNoInteractions(cartMapper);
    }

    @Test
    void addShouldTranslateUniqueConstraintConflict() {
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish(9L, "ON_SALE", 5, "12.50"));
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(category(3L, 1));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(cartMapper.insert(any(Cart.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.addItem(12L, new CartAddRequest(9L, 1)));

        assertEquals(ErrorCode.CART_ITEM_CONFLICT, exception.getErrorCode());
    }

    @Test
    void updateQuantityShouldChangeOnlyCurrentUsersCartItem() {
        Dish dish = dish(9L, "ON_SALE", 5, "12.50");
        Cart existing = cart(4L, 12L, 9L, 1);
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish);
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        when(dishMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dish));

        var result = cartService.updateItemQuantity(12L, 9L,
                new CartQuantityRequest(3));

        assertEquals(3, existing.getQuantity());
        assertEquals(new BigDecimal("37.50"), result.totalAmount());
        verify(cartMapper).updateById(existing);
        verify(cartMapper).selectOne(any(Wrapper.class));
    }

    @Test
    void updateQuantityShouldRejectNonPositiveQuantity() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.updateItemQuantity(12L, 9L,
                        new CartQuantityRequest(0)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(dishMapper, categoryMapper, cartMapper);
    }

    @Test
    void updateQuantityShouldRejectInsufficientStock() {
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish(9L, "ON_SALE", 2, "12.50"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.updateItemQuantity(12L, 9L,
                        new CartQuantityRequest(3)));

        assertEquals(ErrorCode.CART_STOCK_INSUFFICIENT, exception.getErrorCode());
        verifyNoInteractions(cartMapper);
    }

    @Test
    void updateQuantityShouldReturnNotFoundForAnotherUsersItem() {
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish(9L, "ON_SALE", 5, "12.50"));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.updateItemQuantity(88L, 9L,
                        new CartQuantityRequest(3)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(cartMapper).selectOne(any(Wrapper.class));
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    void updateQuantityShouldRejectMissingOrOffSaleDish() {
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.updateItemQuantity(12L, 9L,
                        new edu.campus.canteen.dto.CartQuantityRequest(3)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cartMapper);
    }

    @Test
    void removeShouldDeleteOnlyCurrentUsersCartItem() {
        Cart existing = cart(4L, 12L, 9L, 2);
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(cartMapper.delete(any(Wrapper.class))).thenReturn(1);

        cartService.removeItem(12L, 9L);

        verify(cartMapper).delete(any(Wrapper.class));
    }

    @Test
    void removeShouldReturnNotFoundForAnotherUsersItem() {
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.removeItem(88L, 9L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(cartMapper).selectOne(any(Wrapper.class));
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    private Cart cart(Long id, Long userId, Long dishId, int quantity) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(userId);
        cart.setDishId(dishId);
        cart.setQuantity(quantity);
        return cart;
    }

    private Dish dish(Long id, String status, int stock, String price) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setCategoryId(3L);
        dish.setName("盖饭");
        dish.setImageUrl("dish.png");
        dish.setPrice(new BigDecimal(price));
        dish.setStatus(status);
        dish.setStock(stock);
        dish.setDeleted(0);
        return dish;
    }

    private Category category(Long id, int status) {
        Category category = new Category();
        category.setId(id);
        category.setStatus(status);
        category.setDeleted(0);
        return category;
    }

}
