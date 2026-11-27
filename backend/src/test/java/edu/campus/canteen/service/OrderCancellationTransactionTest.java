package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.OrderCancellationTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancellationTransactionTest {
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private DishMapper dishMapper;
    private OrderCancellationTransaction cancellation;

    @BeforeEach
    void setUp() {
        cancellation = new OrderCancellationTransaction(orderMapper, orderItemMapper, dishMapper);
    }

    @Test
    void pendingOrderCancellationChangesStateAndRestoresSnapshotQuantity() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "PENDING"));
        when(orderMapper.cancelPendingOrder(35L, 7L)).thenReturn(1);
        OrderItem item = new OrderItem();
        item.setDishId(9L);
        item.setQuantity(3);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(dishMapper.restoreStock(9L, 3)).thenReturn(1);

        assertDoesNotThrow(() -> cancellation.cancel(7L, 35L));
        verify(orderMapper).cancelPendingOrder(35L, 7L);
        verify(dishMapper).restoreStock(9L, 3);
    }

    @Test
    void adminCanCancelUnfinishedOrderAndRestoresSnapshotQuantity() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "PREPARING"));
        when(orderMapper.cancelUnfinishedOrder(35L, 8L)).thenReturn(1);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(9L, 3)));
        when(dishMapper.restoreStock(9L, 3)).thenReturn(1);

        assertDoesNotThrow(() -> cancellation.cancelByAdmin(8L, 35L));
        verify(orderMapper).cancelUnfinishedOrder(35L, 8L);
        verify(dishMapper).restoreStock(9L, 3);
    }

    @Test
    void adminCannotCancelCompletedOrAlreadyCancelledOrder() {
        for (String status : List.of("COMPLETED", "CANCELLED")) {
            when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, status));
            BusinessException error = assertThrows(BusinessException.class,
                    () -> cancellation.cancelByAdmin(8L, 35L));
            assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        }
        verify(orderMapper, never()).cancelUnfinishedOrder(any(), any());
        verifyNoInteractions(orderItemMapper, dishMapper);
    }

    @Test
    void concurrentAdminCancellationRestoresInventoryAtMostOnce() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(
                order(7L, "READY"), order(7L, "READY"));
        when(orderMapper.cancelUnfinishedOrder(35L, 8L)).thenReturn(1, 0);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(9L, 2)));
        when(dishMapper.restoreStock(9L, 2)).thenReturn(1);

        cancellation.cancelByAdmin(8L, 35L);
        BusinessException repeated = assertThrows(BusinessException.class,
                () -> cancellation.cancelByAdmin(8L, 35L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, repeated.getErrorCode());
        verify(orderMapper, times(2)).cancelUnfinishedOrder(35L, 8L);
        verify(orderItemMapper, times(1)).selectList(any(Wrapper.class));
        verify(dishMapper, times(1)).restoreStock(9L, 2);
    }

    @Test
    void missingOrForeignOrderIsNotFound() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> cancellation.cancel(7L, 35L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verify(orderMapper, never()).cancelPendingOrder(any(), any());
        verifyNoInteractions(orderItemMapper, dishMapper);
    }

    @Test
    void nonPendingOrderCannotBeCancelled() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "COMPLETED"));
        BusinessException error = assertThrows(BusinessException.class, () -> cancellation.cancel(7L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, never()).cancelPendingOrder(any(), any());
        verifyNoInteractions(orderItemMapper, dishMapper);
    }

    @Test
    void failedConditionalStateUpdateDoesNotRestoreStockAgain() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "PENDING"), order(7L, "PENDING"));
        when(orderMapper.cancelPendingOrder(35L, 7L)).thenReturn(1, 0);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(9L, 2)));
        when(dishMapper.restoreStock(9L, 2)).thenReturn(1);
        cancellation.cancel(7L, 35L);

        BusinessException error = assertThrows(BusinessException.class, () -> cancellation.cancel(7L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, times(2)).cancelPendingOrder(35L, 7L);
        verify(orderItemMapper, times(1)).selectList(any(Wrapper.class));
        verify(dishMapper, times(1)).restoreStock(9L, 2);
    }

    @Test
    void stockRestoreFailureAbortsRemainingWork() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "PENDING"));
        when(orderMapper.cancelPendingOrder(35L, 7L)).thenReturn(1);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(9L, 2)));
        when(dishMapper.restoreStock(9L, 2)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> cancellation.cancel(7L, 35L));
    }

    private Order order(Long userId, String status) {
        Order order = new Order();
        order.setId(35L);
        order.setUserId(userId);
        order.setStatus(status);
        return order;
    }

    private OrderItem item(Long dishId, int quantity) {
        OrderItem item = new OrderItem();
        item.setDishId(dishId);
        item.setQuantity(quantity);
        return item;
    }
}
