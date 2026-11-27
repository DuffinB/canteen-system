package edu.campus.canteen.vo;

/** 登录成功响应。 */
public record LoginResponseVO(String token, UserInfoVO user) {
}
