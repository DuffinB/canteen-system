package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.OrderCancellationTransaction;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(OrderCancellationRollbackTest.TestConfig.class)
class OrderCancellationRollbackTest {
    @Autowired private OrderCancellationTransaction cancellation;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private TransactionProbe probe;

    @BeforeEach
    void setUp() {
        reset(orderMapper, orderItemMapper, dishMapper);
        probe.reset();
        Order order = new Order();
        order.setId(35L);
        order.setUserId(7L);
        order.setStatus("PENDING");
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderMapper.cancelPendingOrder(35L, 7L)).thenAnswer(invocation -> {
            probe.statusChanged++;
            return 1;
        });
        when(orderMapper.cancelUnfinishedOrder(35L, 8L)).thenAnswer(invocation -> {
            probe.statusChanged++;
            return 1;
        });
        OrderItem first = item(9L, 2);
        OrderItem second = item(10L, 1);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(dishMapper.restoreStock(9L, 2)).thenAnswer(invocation -> {
            probe.stockRestored++;
            return 1;
        });
        when(dishMapper.restoreStock(10L, 1)).thenAnswer(invocation -> 0);
    }

    @Test
    void failureAfterStatusAndFirstStockUpdateRollsBackWholeTransaction() {
        assertThrows(IllegalStateException.class, () -> cancellation.cancel(7L, 35L));
        assertEquals(1, probe.rollbackCount);
        assertEquals(0, probe.commitCount);
        assertEquals(0, probe.statusChanged);
        assertEquals(0, probe.stockRestored);
        verify(dishMapper).restoreStock(9L, 2);
        verify(dishMapper).restoreStock(10L, 1);
    }

    @Test
    void adminCancellationFailureRollsBackStatusAndEarlierStockRestores() {
        assertThrows(IllegalStateException.class, () -> cancellation.cancelByAdmin(8L, 35L));
        assertEquals(1, probe.rollbackCount);
        assertEquals(0, probe.commitCount);
        assertEquals(0, probe.statusChanged);
        assertEquals(0, probe.stockRestored);
        verify(orderMapper).cancelUnfinishedOrder(35L, 8L);
    }

    private static OrderItem item(Long dishId, int quantity) {
        OrderItem item = new OrderItem();
        item.setDishId(dishId);
        item.setQuantity(quantity);
        return item;
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean TransactionProbe transactionProbe() { return new TransactionProbe(); }
        @Bean PlatformTransactionManager transactionManager(TransactionProbe probe) {
            return new ProbeTransactionManager(probe);
        }
        @Bean OrderMapper orderMapper() { return mock(OrderMapper.class); }
        @Bean OrderItemMapper orderItemMapper() { return mock(OrderItemMapper.class); }
        @Bean DishMapper dishMapper() { return mock(DishMapper.class); }
        @Bean OrderCancellationTransaction cancellation(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                                         DishMapper dishMapper) {
            return new OrderCancellationTransaction(orderMapper, orderItemMapper, dishMapper);
        }
    }

    static class TransactionProbe {
        int statusChanged;
        int stockRestored;
        int rollbackCount;
        int commitCount;
        void reset() { statusChanged = 0; stockRestored = 0; rollbackCount = 0; commitCount = 0; }
    }

    static class ProbeTransactionManager extends AbstractPlatformTransactionManager {
        private final TransactionProbe probe;
        ProbeTransactionManager(TransactionProbe probe) { this.probe = probe; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { probe.commitCount++; }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            probe.statusChanged = 0;
            probe.stockRestored = 0;
            probe.rollbackCount++;
        }
    }
}
