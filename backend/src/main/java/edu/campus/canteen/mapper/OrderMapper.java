package edu.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.campus.canteen.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT "
            + "(SELECT COUNT(*) FROM orders WHERE created_at >= #{startDate} "
            + "AND created_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY)) AS totalOrders, "
            + "(SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED' AND created_at >= #{startDate} "
            + "AND created_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY)) AS completedOrders, "
            + "COALESCE((SELECT SUM(oi.subtotal) FROM order_items oi "
            + "JOIN orders o ON o.id = oi.order_id WHERE o.status = 'COMPLETED' "
            + "AND o.completed_at >= #{startDate} "
            + "AND o.completed_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY)), 0) AS completedSales")
    edu.campus.canteen.mapper.StatisticsSummaryRow selectStatisticsSummary(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Update("UPDATE orders SET status = 'CANCELLED' "
            + "WHERE id = #{orderId} AND user_id = #{userId} AND status = 'PENDING'")
    int cancelPendingOrder(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Update("UPDATE orders SET status = 'CANCELLED', handled_by = #{adminId} "
            + "WHERE id = #{orderId} AND status IN ('PENDING', 'PREPARING', 'READY')")
    int cancelUnfinishedOrder(@Param("orderId") Long orderId, @Param("adminId") Long adminId);

    @Update("UPDATE orders SET status = 'PREPARING', handled_by = #{adminId} "
            + "WHERE id = #{orderId} AND status = 'PENDING'")
    int confirmPendingOrder(@Param("orderId") Long orderId, @Param("adminId") Long adminId);

    @Update("UPDATE orders SET status = 'READY', handled_by = #{adminId} "
            + "WHERE id = #{orderId} AND status = 'PREPARING'")
    int markPreparingOrderReady(@Param("orderId") Long orderId, @Param("adminId") Long adminId);

    @Update("UPDATE orders SET status = 'COMPLETED', handled_by = #{adminId}, "
            + "completed_at = CURRENT_TIMESTAMP WHERE id = #{orderId} AND status = 'READY'")
    int completeReadyOrder(@Param("orderId") Long orderId, @Param("adminId") Long adminId);

    @Update("UPDATE orders SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{orderId} AND user_id = #{userId} AND status = 'READY'")
    int confirmPickupByStudent(@Param("orderId") Long orderId, @Param("userId") Long userId);
}
