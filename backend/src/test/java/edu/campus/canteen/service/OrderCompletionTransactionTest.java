package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.OrderCompletionTransaction;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCompletionTransactionTest {
    @Mock private OrderMapper orderMapper;
    private OrderCompletionTransaction completion;

    @BeforeEach
    void setUp() {
        completion = new OrderCompletionTransaction(orderMapper);
    }

    @Test
    void readyOrderTransitionsAndRecordsAdmin() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("READY"));
        when(orderMapper.completeReadyOrder(35L, 8L)).thenReturn(1);

        assertDoesNotThrow(() -> completion.complete(8L, 35L));

        verify(orderMapper).completeReadyOrder(35L, 8L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PREPARING", "COMPLETED", "CANCELLED"})
    void otherStatusesCannotBeCompleted(String status) {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(status));

        BusinessException error = assertThrows(BusinessException.class, () -> completion.complete(8L, 35L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, never()).completeReadyOrder(any(), any());
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> completion.complete(8L, 999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verify(orderMapper, never()).completeReadyOrder(any(), any());
    }

    @Test
    void failedConditionalUpdateIsNotReportedAsSuccess() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("READY"));
        when(orderMapper.completeReadyOrder(35L, 8L)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> completion.complete(8L, 35L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper).completeReadyOrder(35L, 8L);
    }

    @Test
    void repeatedCompletionCannotSucceedTwice() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("READY"), order("COMPLETED"));
        when(orderMapper.completeReadyOrder(35L, 8L)).thenReturn(1);
        completion.complete(8L, 35L);

        BusinessException error = assertThrows(BusinessException.class, () -> completion.complete(9L, 35L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, times(1)).completeReadyOrder(any(), any());
    }

    @Test
    void mapperUpdateUsesDatabaseTimeAndReadyConditionAtomically() throws Exception {
        Update update = OrderMapper.class.getMethod("completeReadyOrder", Long.class, Long.class)
                .getAnnotation(Update.class);
        String sql = String.join(" ", Arrays.stream(update.value()).toList());
        assertTrue(sql.contains("status = 'COMPLETED'"));
        assertTrue(sql.contains("handled_by = #{adminId}"));
        assertTrue(sql.contains("completed_at = CURRENT_TIMESTAMP"));
        assertTrue(sql.contains("id = #{orderId} AND status = 'READY'"));
    }

    private Order order(String status) {
        Order order = new Order();
        order.setId(35L);
        order.setStatus(status);
        return order;
    }
}
