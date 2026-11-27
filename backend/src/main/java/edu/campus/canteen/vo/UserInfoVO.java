package edu.campus.canteen.vo;

import java.time.LocalDateTime;

/** 对外用户信息，不包含密码或密码哈希。 */
public record UserInfoVO(
        Long id,
        String studentNo,
        String nickname,
        String role,
        LocalDateTime createdAt
) {
}
