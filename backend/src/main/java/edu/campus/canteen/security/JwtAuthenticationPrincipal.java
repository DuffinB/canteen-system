package edu.campus.canteen.security;

/** 已通过 JWT 验证的最小身份信息。 */
public record JwtAuthenticationPrincipal(Long userId, String role) {
}
