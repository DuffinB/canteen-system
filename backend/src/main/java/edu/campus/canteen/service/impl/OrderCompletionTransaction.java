package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员确认学生已取餐的订单完成事务。 */
@Service
public class OrderCompletionTransaction {
    private final OrderMapper orderMapper;

    public OrderCompletionTransaction(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Transactional
    public void complete(Long adminId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"READY".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }

        // Mapper 在同一条件更新中写入状态、处理人和数据库当前时间。
        if (orderMapper.completeReadyOrder(orderId, adminId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
    }
}
