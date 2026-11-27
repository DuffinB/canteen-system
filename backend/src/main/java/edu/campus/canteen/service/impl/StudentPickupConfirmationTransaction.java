package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学生确认本人 READY 订单的取餐完成状态迁移。 */
@Service
public class StudentPickupConfirmationTransaction {
    private final OrderMapper orderMapper;

    public StudentPickupConfirmationTransaction(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Transactional
    public void confirm(Long userId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"READY".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }

        // 原子更新只修改订单状态和完成时间，不覆盖 handled_by。
        if (orderMapper.confirmPickupByStudent(orderId, userId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
    }
}
