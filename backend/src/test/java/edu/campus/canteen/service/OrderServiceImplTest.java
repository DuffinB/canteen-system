package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import edu.campus.canteen.dto.OrderCreateRequest;
import edu.campus.canteen.dto.OrderQueryRequest;
import edu.campus.canteen.dto.AdminOrderQueryRequest;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.entity.User;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.mapper.UserMapper;
import edu.campus.canteen.service.impl.OrderCreationTransaction;
import edu.campus.canteen.service.impl.OrderCancellationTransaction;
import edu.campus.canteen.service.impl.OrderConfirmationTransaction;
import edu.campus.canteen.service.impl.OrderReadyTransaction;
import edu.campus.canteen.service.impl.OrderCompletionTransaction;
import edu.campus.canteen.service.impl.StudentPickupConfirmationTransaction;
import edu.campus.canteen.service.impl.OrderServiceImpl;
import edu.campus.canteen.vo.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderCreationTransaction orderCreationTransaction;
    @Mock private OrderCancellationTransaction orderCancellationTransaction;
    @Mock private UserMapper userMapper;
    @Mock private OrderConfirmationTransaction orderConfirmationTransaction;
    @Mock private OrderReadyTransaction orderReadyTransaction;
    @Mock private OrderCompletionTransaction orderCompletionTransaction;
    @Mock private StudentPickupConfirmationTransaction studentPickupConfirmationTransaction;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, orderItemMapper, orderCreationTransaction,
                orderCancellationTransaction, userMapper, orderConfirmationTransaction, orderReadyTransaction,
                orderCompletionTransaction, studentPickupConfirmationTransaction);
    }

    @Test
    void repeatedIdempotencyKeyShouldReturnExistingOrderWithoutCreatingAnother() {
        Order existing = order(7L, 35L, "key-repeat");
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var result = orderService.submitOrder(7L, "key-repeat", request());

        assertEquals(35L, result.id());
        verifyNoInteractions(orderCreationTransaction);
    }

    @Test
    void idempotencyKeyOwnedByAnotherStudentShouldBeRejected() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(9L, 35L, "key-other"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.submitOrder(7L, "key-other", request()));

        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(orderCreationTransaction, orderItemMapper);
    }

    @Test
    void concurrentDuplicateInsertShouldReturnCommittedOrder() {
        Order existing = order(7L, 35L, "key-race");
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null, existing);
        when(orderCreationTransaction.create(7L, "key-race", request()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var result = orderService.submitOrder(7L, "key-race", request());

        assertEquals(35L, result.id());
        verify(orderCreationTransaction).create(7L, "key-race", request());
    }

    @Test
    void blankOrLongIdempotencyKeyShouldBeRejected() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                assertThrows(BusinessException.class, () -> orderService.submitOrder(7L, " ", request()))
                        .getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR,
                assertThrows(BusinessException.class, () -> orderService.submitOrder(7L, "x".repeat(65), request()))
                        .getErrorCode());
        verifyNoInteractions(orderMapper, orderCreationTransaction);
    }

    @Test
    void getMyOrdersShouldReturnPagedOrdersAndSnapshotItems() {
        Order first = order(7L, 35L, "key-1");
        Order second = order(7L, 34L, "key-2");
        Page<Order> dbPage = new Page<>(2, 5);
        dbPage.setTotal(6);
        dbPage.setRecords(List.of(first, second));
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dbPage);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                item(35L, 9L, "快照菜名甲", "13.80", 2, "27.60"),
                item(34L, 10L, "快照菜名乙", "8.50", 1, "8.50")));

        PageVO<edu.campus.canteen.vo.OrderVO> result = orderService.getMyOrders(7L,
                new OrderQueryRequest(2, 5, null));

        assertEquals(6, result.total());
        assertEquals(2, result.page());
        assertEquals(5, result.size());
        assertEquals(2, result.records().size());
        assertEquals("快照菜名甲", result.records().get(0).items().get(0).dishName());
        assertEquals(new BigDecimal("13.80"), result.records().get(0).items().get(0).unitPrice());
        assertEquals(new BigDecimal("27.60"), result.records().get(0).items().get(0).subtotal());
        assertEquals("快照菜名乙", result.records().get(1).items().get(0).dishName());
        verify(orderMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void getMyOrdersCanFilterByStatusWhileKeepingUserScope() {
        if (TableInfoHelper.getTableInfo(Order.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace(OrderMapper.class.getName());
            TableInfoHelper.initTableInfo(assistant, Order.class);
        }
        Page<Order> dbPage = new Page<>(1, 10);
        dbPage.setTotal(0);
        dbPage.setRecords(List.of());
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dbPage);

        orderService.getMyOrders(7L, new OrderQueryRequest(1, 10, "CANCELLED"));

        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>> wrapper =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(Page.class), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("status"));
    }

    @Test
    void getMyOrdersShouldReturnEmptyPageWhenStudentHasNoOrders() {
        Page<Order> dbPage = new Page<>(1, 10);
        dbPage.setTotal(0);
        dbPage.setRecords(List.of());
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dbPage);

        PageVO<edu.campus.canteen.vo.OrderVO> result = orderService.getMyOrders(7L,
                new OrderQueryRequest(null, null, null));

        assertTrue(result.records().isEmpty());
        assertEquals(0, result.total());
        assertEquals(1, result.page());
        assertEquals(10, result.size());
        verifyNoInteractions(orderItemMapper);
    }

    @Test
    void getMyOrderShouldReturnOwnOrderWithItemSnapshots() {
        when(orderMapper.selectById(35L)).thenReturn(order(7L, 35L, "key-detail"));
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                item(35L, 9L, "下单菜名快照", "13.80", 2, "27.60")));

        var result = orderService.getMyOrder(7L, 35L);

        assertEquals(35L, result.id());
        assertEquals("下单菜名快照", result.items().get(0).dishName());
        assertEquals(new BigDecimal("13.80"), result.items().get(0).unitPrice());
        assertEquals(2, result.items().get(0).quantity());
        assertEquals(new BigDecimal("27.60"), result.items().get(0).subtotal());
    }

    @Test
    void getMyOrderShouldHideAnotherStudentsOrder() {
        when(orderMapper.selectById(35L)).thenReturn(order(9L, 35L, "key-private"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.getMyOrder(7L, 35L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(orderItemMapper);
    }

    @Test
    void getMyOrderShouldReturnNotFoundForMissingOrder() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.getMyOrder(7L, 999L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(orderItemMapper);
    }

    @Test
    void adminOrderListReturnsOrderSummaryAndSafeStudentFields() {
        Order order = order(7L, 35L, "admin-list");
        Page<Order> dbPage = new Page<>(1, 10);
        dbPage.setTotal(1);
        dbPage.setRecords(List.of(order));
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dbPage);
        User student = new User();
        student.setId(7L);
        student.setStudentNo("student-001");
        student.setNickname("学生甲");
        student.setPasswordHash("should-never-be-returned");
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(student));

        var result = orderService.getAdminOrders(new AdminOrderQueryRequest(1, 10, null));

        assertEquals(1, result.total());
        assertEquals("student-001", result.records().get(0).studentNo());
        assertEquals("学生甲", result.records().get(0).studentNickname());
        assertEquals(35L, result.records().get(0).id());
        verifyNoInteractions(orderItemMapper);
    }

    @Test
    void adminOrderListAddsRequestedStatusFilter() {
        Page<Order> dbPage = new Page<>(1, 10);
        dbPage.setTotal(0);
        dbPage.setRecords(List.of());
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dbPage);

        orderService.getAdminOrders(new AdminOrderQueryRequest(1, 10, "CANCELLED"));

        verify(orderMapper).selectPage(any(Page.class), any(Wrapper.class));
        verifyNoInteractions(userMapper, orderItemMapper);
    }

    @Test
    void adminOrderDetailReturnsStudentAndCompleteItemSnapshots() {
        when(orderMapper.selectById(35L)).thenReturn(order(7L, 35L, "admin-detail"));
        User student = new User();
        student.setId(7L);
        student.setStudentNo("student-001");
        student.setNickname("学生甲");
        when(userMapper.selectById(7L)).thenReturn(student);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                item(35L, 9L, "下单时菜名", "13.80", 2, "27.60")));

        var result = orderService.getAdminOrder(35L);

        assertEquals("student-001", result.studentNo());
        assertEquals("学生甲", result.studentNickname());
        assertEquals("下单时菜名", result.items().get(0).dishName());
        assertEquals(new BigDecimal("13.80"), result.items().get(0).unitPrice());
        assertEquals(new BigDecimal("27.60"), result.items().get(0).subtotal());
    }

    @Test
    void adminOrderDetailMissingOrderIsNotFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> orderService.getAdminOrder(999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(userMapper, orderItemMapper);
    }

    private Order order(Long userId, Long id, String key) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNo("order-number");
        order.setTotalAmount(new BigDecimal("13.80"));
        order.setStatus("PENDING");
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setPickupSlot("LUNCH");
        order.setIdempotencyKey(key);
        return order;
    }

    private OrderItem item(Long orderId, Long dishId, String name, String price, int quantity, String subtotal) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setDishId(dishId);
        item.setDishNameSnapshot(name);
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        item.setSubtotal(new BigDecimal(subtotal));
        return item;
    }

    private OrderCreateRequest request() {
        return new OrderCreateRequest(LocalDate.now().plusDays(1), "LUNCH", null);
    }
}
