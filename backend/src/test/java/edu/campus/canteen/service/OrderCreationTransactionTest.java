package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.dto.OrderCreateRequest;
import edu.campus.canteen.entity.Cart;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.Dish;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.CartMapper;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.OrderCreationTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreationTransactionTest {
    @Mock private CartMapper cartMapper;
    @Mock private DishMapper dishMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    private OrderCreationTransaction orderCreation;

    @BeforeEach
    void setUp() {
        orderCreation = new OrderCreationTransaction(cartMapper, dishMapper, categoryMapper,
                orderMapper, orderItemMapper);
    }

    @Test
    void createOrderShouldCalculateSnapshotsDeductStockAndClearOwnCart() {
        Cart first = cart(10L, 7L, 2L, 2);
        Cart second = cart(11L, 7L, 3L, 1);
        Dish firstDish = dish(2L, "牛肉饭", "12.50", 5, "ON_SALE");
        Dish secondDish = dish(3L, "豆浆", "8.00", 5, "ON_SALE");
        Order savedOrder = new Order();
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(30L);
            savedOrder.setId(30L);
            savedOrder.setOrderNo(order.getOrderNo());
            savedOrder.setUserId(order.getUserId());
            savedOrder.setStatus(order.getStatus());
            savedOrder.setPickupDate(order.getPickupDate());
            savedOrder.setPickupSlot(order.getPickupSlot());
            savedOrder.setRemark(order.getRemark());
            return 1;
        });
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second), List.of(first, second));
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(firstDish, secondDish);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(category(1));
        when(dishMapper.deductStock(2L, 2)).thenReturn(1);
        when(dishMapper.deductStock(3L, 1)).thenReturn(1);
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);
        when(cartMapper.delete(any(Wrapper.class))).thenReturn(2);
        when(orderMapper.updateById(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            savedOrder.setTotalAmount(order.getTotalAmount());
            return 1;
        });
        when(orderMapper.selectById(30L)).thenReturn(savedOrder);

        var result = orderCreation.create(7L, "key-123", request());

        assertEquals("PENDING", result.status());
        assertEquals(new BigDecimal("33.00"), result.totalAmount());
        assertEquals(2, result.items().size());
        assertEquals("牛肉饭", result.items().get(0).dishName());
        assertEquals(new BigDecimal("12.50"), result.items().get(0).unitPrice());
        assertEquals(new BigDecimal("25.00"), result.items().get(0).subtotal());
        assertEquals(new BigDecimal("8.00"), result.items().get(1).subtotal());

        ArgumentCaptor<OrderItem> items = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper, times(2)).insert(items.capture());
        assertEquals(List.of("牛肉饭", "豆浆"), items.getAllValues().stream()
                .map(OrderItem::getDishNameSnapshot).toList());
        verify(dishMapper).deductStock(2L, 2);
        verify(dishMapper).deductStock(3L, 1);
        verify(cartMapper).delete(any(Wrapper.class));
    }

    @Test
    void emptyCartShouldFailWithoutChangingInventoryOrCreatingItems() {
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(30L);
            return 1;
        });
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderCreation.create(7L, "key-empty", request()));

        assertEquals(ErrorCode.CART_EMPTY, exception.getErrorCode());
        verifyNoInteractions(dishMapper, categoryMapper, orderItemMapper);
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void insufficientStockShouldAbortBeforeOrderItemsAndCartDeletion() {
        Cart row = cart(10L, 7L, 2L, 4);
        prepareOneItem(row, dish(2L, "牛肉饭", "12.50", 3, "ON_SALE"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderCreation.create(7L, "key-stock", request()));

        assertEquals(ErrorCode.ORDER_STOCK_INSUFFICIENT, exception.getErrorCode());
        verify(dishMapper, never()).deductStock(anyLong(), anyInt());
        verifyNoInteractions(orderItemMapper);
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void atomicStockUpdateFailureShouldAbortOrder() {
        Cart row = cart(10L, 7L, 2L, 2);
        prepareOneItem(row, dish(2L, "牛肉饭", "12.50", 5, "ON_SALE"));
        when(dishMapper.deductStock(2L, 2)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderCreation.create(7L, "key-race", request()));

        assertEquals(ErrorCode.ORDER_STOCK_INSUFFICIENT, exception.getErrorCode());
        verifyNoInteractions(orderItemMapper);
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void offSaleDishShouldAbortOrder() {
        Cart row = cart(10L, 7L, 2L, 1);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(30L);
            return 1;
        });
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish(2L, "牛肉饭", "12.50", 5, "OFF_SALE"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderCreation.create(7L, "key-off-sale", request()));

        assertEquals(ErrorCode.INVALID_DISH_STATUS, exception.getErrorCode());
        verifyNoInteractions(categoryMapper, orderItemMapper);
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void missingOrLogicallyDeletedDishShouldAbortOrder() {
        Cart row = cart(10L, 7L, 2L, 1);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(30L);
            return 1;
        });
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderCreation.create(7L, "key-deleted-dish", request()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(categoryMapper, orderItemMapper);
        verify(dishMapper, never()).deductStock(anyLong(), anyInt());
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void disabledCategoryShouldAbortOrder() {
        Cart row = cart(10L, 7L, 2L, 1);
        prepareOneItem(row, dish(2L, "牛肉饭", "12.50", 5, "ON_SALE"), 0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderCreation.create(7L, "key-category", request()));

        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, exception.getErrorCode());
        verify(dishMapper, never()).deductStock(anyLong(), anyInt());
        verifyNoInteractions(orderItemMapper);
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    private void prepareOneItem(Cart row, Dish dish) {
        prepareOneItem(row, dish, 1);
    }

    private void prepareOneItem(Cart row, Dish dish, int categoryStatus) {
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(30L);
            return 1;
        });
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row), List.of(row));
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(category(categoryStatus));
    }

    private OrderCreateRequest request() {
        return new OrderCreateRequest(LocalDate.now().plusDays(1), "LUNCH", "少辣");
    }

    private Cart cart(Long id, Long userId, Long dishId, int quantity) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(userId);
        cart.setDishId(dishId);
        cart.setQuantity(quantity);
        return cart;
    }

    private Dish dish(Long id, String name, String price, int stock, String status) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setCategoryId(1L);
        dish.setName(name);
        dish.setPrice(new BigDecimal(price));
        dish.setStock(stock);
        dish.setStatus(status);
        dish.setDeleted(0);
        return dish;
    }

    private Category category(int status) {
        Category category = new Category();
        category.setId(1L);
        category.setStatus(status);
        category.setDeleted(0);
        return category;
    }
}
