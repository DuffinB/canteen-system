package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.service.impl.StudentPickupConfirmationTransaction;
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
class StudentPickupConfirmationTransactionTest {
    @Mock private OrderMapper orderMapper;
    private StudentPickupConfirmationTransaction confirmation;

    @BeforeEach
    void setUp() {
        confirmation = new StudentPickupConfirmationTransaction(orderMapper);
    }

    @Test
    void ownReadyOrderCanBeCompletedByStudent() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "READY"));
        when(orderMapper.confirmPickupByStudent(35L, 7L)).thenReturn(1);

        assertDoesNotThrow(() -> confirmation.confirm(7L, 35L));

        verify(orderMapper).confirmPickupByStudent(35L, 7L);
    }

    @Test
    void missingOrForeignOrderReturnsNotFound() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(7L, 35L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verify(orderMapper, never()).confirmPickupByStudent(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PREPARING", "COMPLETED", "CANCELLED"})
    void nonReadyOrderCannotBeCompleted(String status) {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, status));
        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(7L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, never()).confirmPickupByStudent(any(), any());
    }

    @Test
    void failedConditionalUpdateDoesNotReportSuccess() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "READY"));
        when(orderMapper.confirmPickupByStudent(35L, 7L)).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(7L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper).confirmPickupByStudent(35L, 7L);
    }

    @Test
    void repeatedConfirmationCannotSucceedTwice() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(7L, "READY"), order(7L, "COMPLETED"));
        when(orderMapper.confirmPickupByStudent(35L, 7L)).thenReturn(1);
        confirmation.confirm(7L, 35L);
        BusinessException error = assertThrows(BusinessException.class, () -> confirmation.confirm(7L, 35L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(orderMapper, times(1)).confirmPickupByStudent(any(), any());
    }

    @Test
    void conditionalUpdateIncludesOwnerAndReadyAndLeavesAdminHandlerUntouched() throws Exception {
        Update update = OrderMapper.class.getMethod("confirmPickupByStudent", Long.class, Long.class)
                .getAnnotation(Update.class);
        String sql = String.join(" ", Arrays.stream(update.value()).toList());
        assertTrue(sql.contains("status = 'COMPLETED'"));
        assertTrue(sql.contains("completed_at = CURRENT_TIMESTAMP"));
        assertTrue(sql.contains("id = #{orderId} AND user_id = #{userId} AND status = 'READY'"));
        assertFalse(sql.contains("handled_by"));
    }

    private Order order(Long userId, String status) {
        Order order = new Order();
        order.setId(35L);
        order.setUserId(userId);
        order.setStatus(status);
        return order;
    }
}
