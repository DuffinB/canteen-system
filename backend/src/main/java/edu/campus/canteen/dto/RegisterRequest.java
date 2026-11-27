package edu.campus.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 学生注册请求。密码仅用于请求校验和哈希，不应记录日志。 */
public record RegisterRequest(
        @NotBlank(message = "学号不能为空")
        @Size(max = 32, message = "学号长度不能超过32个字符")
        String studentNo,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度须为8至64个字符")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称长度不能超过50个字符")
        String nickname
) {
}
