package edu.campus.canteen.vo;

import java.time.LocalDateTime;

/** 管理员用户列表视图，不包含密码或密码哈希。 */
public record AdminUserVO(Long id, String studentNo, String nickname, String role,
                          Integer status, LocalDateTime createdAt) {
}
