package edu.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.campus.canteen.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.time.LocalDate;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    @Select("SELECT d.* FROM dishes d "
            + "JOIN categories c ON c.id = d.category_id AND c.status = 1 AND c.deleted = 0 "
            + "JOIN order_items oi ON oi.dish_id = d.id "
            + "JOIN orders o ON o.id = oi.order_id AND o.status = 'COMPLETED' "
            + "WHERE d.deleted = 0 AND d.status = 'ON_SALE' "
            + "GROUP BY d.id ORDER BY SUM(oi.quantity) DESC, d.id ASC LIMIT 10")
    List<Dish> selectTopSellingDishes();

    @Select("SELECT d.id AS dishId, d.name AS dishName, SUM(oi.quantity) AS salesQuantity "
            + "FROM order_items oi JOIN orders o ON o.id = oi.order_id AND o.status = 'COMPLETED' "
            + "JOIN dishes d ON d.id = oi.dish_id "
            + "WHERE o.completed_at >= #{startDate} "
            + "AND o.completed_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY) "
            + "GROUP BY d.id, d.name ORDER BY SUM(oi.quantity) DESC, d.id ASC")
    List<DishSalesSummaryRow> selectDishSalesSummary(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    @Update("UPDATE dishes SET stock = stock - #{quantity} "
            + "WHERE id = #{dishId} AND deleted = 0 AND status = 'ON_SALE' AND stock >= #{quantity}")
    int deductStock(@Param("dishId") Long dishId, @Param("quantity") int quantity);

    /** 按历史订单明细回补库存，故意不排除逻辑删除菜品。 */
    @Update("UPDATE dishes SET stock = stock + #{quantity} WHERE id = #{dishId}")
    int restoreStock(@Param("dishId") Long dishId, @Param("quantity") int quantity);
}
