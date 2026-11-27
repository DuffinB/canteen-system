package edu.campus.canteen.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 菜品对外视图，不包含逻辑删除字段等内部管理信息。 */
public record DishVO(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        Integer stock,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
