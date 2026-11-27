package edu.campus.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录请求。不要记录该对象或其中的密码。 */
public record LoginRequest(
        @NotBlank(message = "学号不能为空")
        @Size(max = 32, message = "学号长度不能超过32个字符")
        String studentNo,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码长度不能超过64个字符")
        String password
) {
}
