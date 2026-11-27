package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.dto.OrderCreateRequest;
import edu.campus.canteen.entity.Cart;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.Dish;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.mapper.CartMapper;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.OrderCreationTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(OrderTransactionRollbackTest.TestConfig.class)
class OrderTransactionRollbackTest {
    @Autowired private OrderCreationTransaction orderCreation;
    @Autowired private CartMapper cartMapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private TransactionProbe transactionProbe;

    @BeforeEach
    void setUp() {
        reset(cartMapper, dishMapper, categoryMapper, orderMapper, orderItemMapper);
        transactionProbe.reset();

        Cart cart = new Cart();
        cart.setId(11L);
        cart.setUserId(7L);
        cart.setDishId(2L);
        cart.setQuantity(1);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            transactionProbe.orderInserted++;
            ((Order) invocation.getArgument(0)).setId(40L);
            return 1;
        });
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cart), List.of(cart));

        Dish dish = new Dish();
        dish.setId(2L);
        dish.setCategoryId(3L);
        dish.setName("触发明细失败");
        dish.setPrice(new BigDecimal("10.00"));
        dish.setStock(4);
        dish.setStatus("ON_SALE");
        dish.setDeleted(0);
        when(dishMapper.selectOne(any(Wrapper.class))).thenReturn(dish);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Category category = new Category();
            category.setId(3L);
            category.setStatus(1);
            category.setDeleted(0);
            return category;
        });
        when(dishMapper.deductStock(2L, 1)).thenAnswer(invocation -> {
            transactionProbe.stockDeducted++;
            return 1;
        });
        when(orderMapper.updateById(any(Order.class))).thenAnswer(invocation -> {
            transactionProbe.orderUpdated++;
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItem.class))).thenAnswer(invocation -> {
            transactionProbe.itemInserted++;
            throw new IllegalStateException("simulated order-item write failure");
        });
        when(cartMapper.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            transactionProbe.cartDeleted++;
            return 1;
        });
    }

    @Test
    void failureAfterStockDeductionRollsBackAllOrderWork() {
        assertThrows(IllegalStateException.class, () -> orderCreation.create(7L, "rollback-key",
                new OrderCreateRequest(LocalDate.now().plusDays(1), "LUNCH", null)));

        assertEquals(1, transactionProbe.rollbackCount);
        assertEquals(0, transactionProbe.commitCount);
        assertFalse(transactionProbe.active);
        assertEquals(0, transactionProbe.orderInserted);
        assertEquals(0, transactionProbe.orderUpdated);
        assertEquals(0, transactionProbe.stockDeducted);
        assertEquals(0, transactionProbe.itemInserted);
        assertEquals(0, transactionProbe.cartDeleted);
        verify(dishMapper).deductStock(2L, 1);
        verify(orderMapper).insert(any(Order.class));
        verify(orderItemMapper).insert(any(OrderItem.class));
        verify(cartMapper, never()).delete(any(Wrapper.class));
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean TransactionProbe transactionProbe() { return new TransactionProbe(); }
        @Bean PlatformTransactionManager transactionManager(TransactionProbe probe) {
            return new ProbeTransactionManager(probe);
        }
        @Bean CartMapper cartMapper() { return mock(CartMapper.class); }
        @Bean DishMapper dishMapper() { return mock(DishMapper.class); }
        @Bean CategoryMapper categoryMapper() { return mock(CategoryMapper.class); }
        @Bean OrderMapper orderMapper() { return mock(OrderMapper.class); }
        @Bean OrderItemMapper orderItemMapper() { return mock(OrderItemMapper.class); }
        @Bean OrderCreationTransaction orderCreationTransaction(CartMapper cartMapper, DishMapper dishMapper,
                CategoryMapper categoryMapper, OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
            return new OrderCreationTransaction(cartMapper, dishMapper, categoryMapper, orderMapper, orderItemMapper);
        }
    }

    static class TransactionProbe {
        int orderInserted;
        int orderUpdated;
        int stockDeducted;
        int itemInserted;
        int cartDeleted;
        int rollbackCount;
        int commitCount;
        boolean active;

        void reset() {
            orderInserted = 0;
            orderUpdated = 0;
            stockDeducted = 0;
            itemInserted = 0;
            cartDeleted = 0;
            rollbackCount = 0;
            commitCount = 0;
            active = false;
        }

        void restoreOnRollback() {
            orderInserted = 0;
            orderUpdated = 0;
            stockDeducted = 0;
            itemInserted = 0;
            cartDeleted = 0;
            rollbackCount++;
            active = false;
        }
    }

    static class ProbeTransactionManager extends AbstractPlatformTransactionManager {
        private final TransactionProbe probe;

        ProbeTransactionManager(TransactionProbe probe) {
            this.probe = probe;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            probe.active = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            probe.commitCount++;
            probe.active = false;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            probe.restoreOnRollback();
        }
    }
}
