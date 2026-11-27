package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员将备餐中的订单标记为可取餐。 */
@Service
public class OrderReadyTransaction {
    private final OrderMapper orderMapper;

    public OrderReadyTransaction(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Transactional
    public void markReady(Long adminId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PREPARING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }

        // 仅原状态仍为 PREPARING 时，同时迁移状态并记录最近处理的管理员。
        if (orderMapper.markPreparingOrderReady(orderId, adminId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
    }
}
