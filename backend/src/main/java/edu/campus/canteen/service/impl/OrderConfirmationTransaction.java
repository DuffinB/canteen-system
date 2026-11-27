package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员确认接单的状态迁移事务。 */
@Service
public class OrderConfirmationTransaction {
    private final OrderMapper orderMapper;

    public OrderConfirmationTransaction(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Transactional
    public void confirm(Long adminId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }

        // 原子条件更新是最终保护；只有一行成功转换状态并设置处理人。
        if (orderMapper.confirmPendingOrder(orderId, adminId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
    }
}
