package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.OrderReadyTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReadyTransactionTest {
    @Mock private OrderMapper orderMapper;
    private OrderReadyTransaction readyTransaction;

    @BeforeEach
    void setUp() {
        readyTransaction = new OrderReadyTransaction(orderMapper);
    }

    @Test
    void preparingOrderTransitionsToReadyAndRecordsAdmin() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("PREPARING"));
        when(orderMapper.markPreparingOrderReady(35L, 8L)).thenReturn(1);

        assertDoesNotThrow(() -> readyTransaction.markReady(8L, 35L));

        verify(orderMapper).markPreparingOrderReady(35L, 8L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "READY", "CANCELLED", "COMPLETED"})
    void otherStatusesCannotTransitionToReady(String status) {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(status));
        BusinessException error = assertThrows(BusinessException.class,
                () -> readyTransaction.markReady(8L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, never()).markPreparingOrderReady(any(), any());
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> readyTransaction.markReady(8L, 999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verify(orderMapper, never()).markPreparingOrderReady(any(), any());
    }

    @Test
    void failedConditionalUpdateDoesNotReportSuccessOrChangeHandler() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("PREPARING"));
        when(orderMapper.markPreparingOrderReady(35L, 8L)).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class,
                () -> readyTransaction.markReady(8L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper).markPreparingOrderReady(35L, 8L);
    }

    @Test
    void repeatedOperationCannotSucceedTwice() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("PREPARING"), order("READY"));
        when(orderMapper.markPreparingOrderReady(35L, 8L)).thenReturn(1);
        readyTransaction.markReady(8L, 35L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> readyTransaction.markReady(9L, 35L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, times(1)).markPreparingOrderReady(any(), any());
    }

    private Order order(String status) {
        Order order = new Order();
        order.setId(35L);
        order.setStatus(status);
        return order;
    }
}
