package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 订单状态更新与库存回补必须处于同一事务。 */
@Service
public class OrderCancellationTransaction {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;

    public OrderCancellationTransaction(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                        DishMapper dishMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.dishMapper = dishMapper;
    }

    @Transactional
    public void cancel(Long userId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }

        // 条件更新是第二道并发保护；状态未成功改变时绝不回补库存。
        if (orderMapper.cancelPendingOrder(orderId, userId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }

        restoreOrderItems(orderId);
    }

    @Transactional
    public void cancelByAdmin(Long adminId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!isUnfinished(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        if (orderMapper.cancelUnfinishedOrder(orderId, adminId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        restoreOrderItems(orderId);
    }

    private boolean isUnfinished(String status) {
        return "PENDING".equals(status) || "PREPARING".equals(status) || "READY".equals(status);
    }

    private void restoreOrderItems(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getDishId)
                .orderByAsc(OrderItem::getId));
        for (OrderItem item : items) {
            if (item.getDishId() == null || item.getQuantity() == null || item.getQuantity() <= 0
                    || dishMapper.restoreStock(item.getDishId(), item.getQuantity()) != 1) {
                throw new IllegalStateException("订单库存回补失败");
            }
        }
    }
}
