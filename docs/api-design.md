# API 接口设计

## 通用规范

基础路径 `/api`，JSON UTF-8；分页 `page` 从 1 开始、`size` 默认 10 最大 100。认证头 `Authorization: Bearer <JWT>`。统一成功 `{ "code":"OK", "message":"成功", "data":{} }`；错误同结构且 data=null。分页 data 为 `records,total,page,size`。HTTP 状态：400 参数错、401 未认证、403 无权限、404 不存在/不可见、409 状态冲突。日期 `YYYY-MM-DD`，时间戳 ISO 8601。客户端不得提供可信价格、金额、用户角色或最终状态。

## 认证接口

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| POST | `/auth/register` | 公开 | `{studentNo,password,nickname}`，固定注册学生 |
| POST | `/auth/login` | 公开 | `{studentNo,password}`，返回 JWT 和基本资料 |
| GET | `/auth/me` | 已登录 | 当前用户和角色 |

退出由前端删除令牌，不做黑名单。

## 学生及公开接口

| 方法 | 路径 | 权限/用途 |
|---|---|---|
| GET | `/categories` | 公开启用分类 |
| GET | `/dishes` | 公开分页；categoryId、keyword、sort 筛选 |
| GET | `/dishes/{id}` | 公开详情 |
| GET | `/dishes/hot` | 公开热门前 10 |
| GET | `/dishes/{id}/reviews` | 公开已审核评价 |
| GET/POST | `/cart`、`/cart/items` | 学生本人读取/加入购物车 `{dishId,quantity}` |
| PUT/DELETE | `/cart/items/{id}` | 学生本人改数量/删除 |
| POST | `/orders` | 学生下单，使用当前全部购物车商品 |
| GET | `/orders` | 学生本人列表分页，可按状态筛选 |
| GET | `/orders/{id}` | 学生本人详情 |
| POST | `/orders/{id}/cancel` | 本人 PENDING 订单取消 |
| POST | `/orders/{id}/confirm-pickup` | 本人 READY 订单确认完成 |
| POST | `/orders/{id}/items/{itemId}/reviews` | 完成订单评价 `{rating,content}` |
| GET/PUT | `/users/me` | 读取/修改本人允许编辑资料 |

下单请求示例：`{ "pickupDate":"2026-11-26", "pickupSlot":"LUNCH", "remark":"少辣" }`，响应头可带 `Idempotency-Key: UUID`，重试复用。后端重新核价、校验库存、预约时间；成功后清空提交商品购物车。首期不支持购物车部分下单。

## 管理员接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST | `/admin/categories` | 分类列表/新增 |
| PUT/PATCH | `/admin/categories/{id}`、`.../status` | 编辑/启停 |
| GET/POST | `/admin/dishes` | 菜品列表/新增 |
| GET/PUT/PATCH | `/admin/dishes/{id}`、`.../status` | 详情/修改（含价格库存）/上下架 |
| GET | `/admin/orders` | 全部订单分页筛选 |
| GET | `/admin/orders/{id}` | 订单详情 |
| POST | `/admin/orders/{id}/confirm` | PENDING→PREPARING |
| POST | `/admin/orders/{id}/ready` | PREPARING→READY |
| POST | `/admin/orders/{id}/complete` | READY→COMPLETED |
| POST | `/admin/orders/{id}/cancel` | 合规则取消并回补库存 |
| GET/PATCH | `/admin/users`、`/admin/users/{id}/status` | 用户查询/启停 |
| GET/PATCH | `/admin/reviews`、`/admin/reviews/{id}/moderation` | 评论查询/审核 |
| GET | `/admin/statistics/summary` | 日期范围订单数、完成数、销售额 |
| GET | `/admin/statistics/dishes` | 日期范围完成销量排行 |

管理员创建通过受控初始化，不开放 API。

## 错误码

`VALIDATION_ERROR` 400、`AUTH_REQUIRED` 401、`FORBIDDEN` 403、`RESOURCE_NOT_FOUND` 404、`DUPLICATE_ACCOUNT` 409、`DISH_UNAVAILABLE` 409、`ORDER_STOCK_INSUFFICIENT` 409、`ORDER_STATUS_INVALID` 409、`PICKUP_TIME_INVALID` 400、`REVIEW_NOT_ALLOWED` 409。Service 校验资源归属、订单状态和评价资格；订单接口只允许明确状态迁移。
