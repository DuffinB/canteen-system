package edu.campus.canteen.exception;

import org.springframework.http.HttpStatus;

/** 当前认证功能使用的业务错误码和对应 HTTP 状态。 */
public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数不合法", HttpStatus.BAD_REQUEST),
    DUPLICATE_ACCOUNT("DUPLICATE_ACCOUNT", "学号已注册", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "账号或密码错误", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "账号已禁用", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    CATEGORY_UNAVAILABLE("CATEGORY_UNAVAILABLE", "菜品分类不存在或已停用", HttpStatus.CONFLICT),
    CATEGORY_NAME_EXISTS("CATEGORY_NAME_EXISTS", "分类名称已存在", HttpStatus.CONFLICT),
    ADMIN_CANNOT_DISABLE_SELF("ADMIN_CANNOT_DISABLE_SELF", "不能停用当前管理员账号", HttpStatus.CONFLICT),
    INVALID_DISH_STATUS("INVALID_DISH_STATUS", "菜品状态不合法", HttpStatus.BAD_REQUEST),
    CART_STOCK_INSUFFICIENT("CART_STOCK_INSUFFICIENT", "菜品库存不足", HttpStatus.CONFLICT),
    CART_ITEM_CONFLICT("CART_ITEM_CONFLICT", "购物车商品发生并发冲突，请重试", HttpStatus.CONFLICT),
    CART_EMPTY("CART_EMPTY", "购物车为空，无法提交订单", HttpStatus.CONFLICT),
    ORDER_STOCK_INSUFFICIENT("ORDER_STOCK_INSUFFICIENT", "部分菜品库存不足，请刷新购物车后重试", HttpStatus.CONFLICT),
    ORDER_CART_CHANGED("ORDER_CART_CHANGED", "购物车内容已变化，请重新提交订单", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_CONFLICT("IDEMPOTENCY_KEY_CONFLICT", "幂等键已被其他账号使用", HttpStatus.CONFLICT),
    ORDER_STATUS_INVALID("ORDER_STATUS_INVALID", "当前订单状态不允许执行此操作", HttpStatus.CONFLICT),
    REVIEW_NOT_ALLOWED("REVIEW_NOT_ALLOWED", "当前订单或订单明细不符合评价条件", HttpStatus.CONFLICT),
    REVIEW_ALREADY_EXISTS("REVIEW_ALREADY_EXISTS", "该订单明细已经评价", HttpStatus.CONFLICT),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
