package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.OrderConfirmationTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationTransactionTest {
    @Mock private OrderMapper orderMapper;
    private OrderConfirmationTransaction confirmation;

    @BeforeEach
    void setUp() {
        confirmation = new OrderConfirmationTransaction(orderMapper);
    }

    @Test
    void pendingOrderTransitionsAndRecordsAdmin() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("PENDING"));
        when(orderMapper.confirmPendingOrder(35L, 8L)).thenReturn(1);

        assertDoesNotThrow(() -> confirmation.confirm(8L, 35L));

        verify(orderMapper).confirmPendingOrder(35L, 8L);
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(8L, 999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verify(orderMapper, never()).confirmPendingOrder(any(), any());
    }

    @Test
    void nonPendingOrderCannotBeConfirmed() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("CANCELLED"));
        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(8L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, never()).confirmPendingOrder(any(), any());
    }

    @Test
    void failedConditionalUpdateDoesNotReportSuccess() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("PENDING"));
        when(orderMapper.confirmPendingOrder(35L, 8L)).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(8L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper).confirmPendingOrder(35L, 8L);
    }

    @Test
    void repeatedConfirmationCannotChangeHandlerAgain() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("PENDING"), order("PREPARING"));
        when(orderMapper.confirmPendingOrder(35L, 8L)).thenReturn(1);
        confirmation.confirm(8L, 35L);

        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(9L, 35L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, times(1)).confirmPendingOrder(any(), any());
    }

    private Order order(String status) {
        Order order = new Order();
        order.setId(35L);
        order.setStatus(status);
        return order;
    }
}
