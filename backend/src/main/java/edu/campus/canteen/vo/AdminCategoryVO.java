package edu.campus.canteen.vo;

import java.time.LocalDateTime;

public record AdminCategoryVO(Long id, String name, Integer sortOrder, Integer status,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
