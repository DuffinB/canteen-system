package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import edu.campus.canteen.service.OrderService;
import edu.campus.canteen.vo.OrderItemVO;
import edu.campus.canteen.vo.PageVO;
import edu.campus.canteen.vo.OrderVO;
import edu.campus.canteen.vo.AdminOrderDetailVO;
import edu.campus.canteen.vo.AdminOrderSummaryVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderCreationTransaction orderCreationTransaction;
    private final OrderCancellationTransaction orderCancellationTransaction;
    private final UserMapper userMapper;
    private final OrderConfirmationTransaction orderConfirmationTransaction;
    private final OrderReadyTransaction orderReadyTransaction;
    private final OrderCompletionTransaction orderCompletionTransaction;
    private final StudentPickupConfirmationTransaction studentPickupConfirmationTransaction;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                            OrderCreationTransaction orderCreationTransaction,
                            OrderCancellationTransaction orderCancellationTransaction,
                            UserMapper userMapper,
                            OrderConfirmationTransaction orderConfirmationTransaction,
                            OrderReadyTransaction orderReadyTransaction,
                            OrderCompletionTransaction orderCompletionTransaction,
                            StudentPickupConfirmationTransaction studentPickupConfirmationTransaction) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderCreationTransaction = orderCreationTransaction;
        this.orderCancellationTransaction = orderCancellationTransaction;
        this.userMapper = userMapper;
        this.orderConfirmationTransaction = orderConfirmationTransaction;
        this.orderReadyTransaction = orderReadyTransaction;
        this.orderCompletionTransaction = orderCompletionTransaction;
        this.studentPickupConfirmationTransaction = studentPickupConfirmationTransaction;
    }

    @Override
    public OrderVO submitOrder(Long userId, String idempotencyKey, OrderCreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Order existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return toOwnedOrder(existing, userId);
        }

        try {
            return orderCreationTransaction.create(userId, idempotencyKey, request);
        } catch (DuplicateKeyException exception) {
            // 并发重放时，唯一索引使失败事务回滚；之后返回已提交的原订单，不再次扣库存。
            Order duplicate = findByIdempotencyKey(idempotencyKey);
            if (duplicate == null) {
                throw exception;
            }
            return toOwnedOrder(duplicate, userId);
        }
    }

    @Override
    public PageVO<OrderVO> getMyOrders(Long userId, OrderQueryRequest query) {
        Page<Order> page = orderMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(query.status() != null, Order::getStatus, query.status())
                        .orderByDesc(Order::getCreatedAt)
                        .orderByDesc(Order::getId));
        if (page.getRecords().isEmpty()) {
            return new PageVO<>(List.of(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
        }

        List<Long> orderIds = page.getRecords().stream().map(Order::getId).toList();
        Map<Long, List<OrderItemVO>> itemsByOrderId = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds)
                        .orderByAsc(OrderItem::getOrderId)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId,
                        Collectors.mapping(this::toOrderItemVO, Collectors.toList())));

        List<OrderVO> records = page.getRecords().stream()
                .map(order -> toOrderVO(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();
        return new PageVO<>(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public OrderVO getMyOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<OrderItemVO> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
                        .orderByAsc(OrderItem::getId))
                .stream().map(this::toOrderItemVO).toList();
        return toOrderVO(order, items);
    }

    @Override
    public void cancelMyOrder(Long userId, Long orderId) {
        orderCancellationTransaction.cancel(userId, orderId);
    }

    @Override
    public PageVO<AdminOrderSummaryVO> getAdminOrders(AdminOrderQueryRequest query) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(query.status() != null, Order::getStatus, query.status())
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId);
        Page<Order> page = orderMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageVO<>(List.of(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
        }

        List<Long> studentIds = page.getRecords().stream().map(Order::getUserId).distinct().toList();
        Map<Long, User> usersById = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, studentIds))
                .stream().collect(Collectors.toMap(User::getId, user -> user));
        List<AdminOrderSummaryVO> records = page.getRecords().stream()
                .map(order -> toAdminOrderSummary(order, usersById.get(order.getUserId())))
                .toList();
        return new PageVO<>(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public AdminOrderDetailVO getAdminOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        User student = userMapper.selectById(order.getUserId());
        List<OrderItemVO> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .orderByAsc(OrderItem::getId))
                .stream().map(this::toOrderItemVO).toList();
        return new AdminOrderDetailVO(order.getId(), order.getOrderNo(), order.getUserId(),
                student == null ? null : student.getStudentNo(), student == null ? null : student.getNickname(),
                order.getTotalAmount(), order.getStatus(), order.getPickupDate(), order.getPickupSlot(),
                order.getRemark(), order.getHandledBy(), order.getCompletedAt(), order.getCreatedAt(), items);
    }

    @Override
    public void confirmAdminOrder(Long adminId, Long orderId) {
        orderConfirmationTransaction.confirm(adminId, orderId);
    }

    @Override
    public void markAdminOrderReady(Long adminId, Long orderId) {
        orderReadyTransaction.markReady(adminId, orderId);
    }

    @Override
    public void completeAdminOrder(Long adminId, Long orderId) {
        orderCompletionTransaction.complete(adminId, orderId);
    }

    @Override
    public void cancelAdminOrder(Long adminId, Long orderId) {
        orderCancellationTransaction.cancelByAdmin(adminId, orderId);
    }

    @Override
    public void confirmPickup(Long userId, Long orderId) {
        studentPickupConfirmationTransaction.confirm(userId, orderId);
    }

    private AdminOrderSummaryVO toAdminOrderSummary(Order order, User student) {
        return new AdminOrderSummaryVO(order.getId(), order.getOrderNo(), order.getUserId(),
                student == null ? null : student.getStudentNo(), student == null ? null : student.getNickname(),
                order.getTotalAmount(), order.getStatus(), order.getPickupDate(), order.getPickupSlot(),
                order.getCreatedAt(), order.getCompletedAt());
    }

    private Order findByIdempotencyKey(String idempotencyKey) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getIdempotencyKey, idempotencyKey));
    }

    private OrderVO toOwnedOrder(Order order, Long userId) {
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        List<OrderItemVO> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
                        .orderByAsc(OrderItem::getId))
                .stream()
                .map(this::toOrderItemVO)
                .toList();
        return toOrderVO(order, items);
    }

    private OrderItemVO toOrderItemVO(OrderItem item) {
        return new OrderItemVO(item.getDishId(), item.getDishNameSnapshot(),
                item.getUnitPrice(), item.getQuantity(), item.getSubtotal());
    }

    private OrderVO toOrderVO(Order order, List<OrderItemVO> items) {
        return new OrderVO(order.getId(), order.getOrderNo(), order.getTotalAmount(), order.getStatus(),
                order.getPickupDate(), order.getPickupSlot(), order.getRemark(), items, order.getCreatedAt());
    }
}
