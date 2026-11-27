# API 接口设计

## 通用约定

- API 前缀为 `/api`，请求和响应均使用 UTF-8 JSON。
- 成功响应：`{"code":"OK","message":"成功","data":...}`；错误响应保持相同外层结构，`data` 为 `null`。
- 分页响应字段为 `records`、`total`、`page`、`size`；`page` 从 1 开始，`size` 默认 10，最大 100。
- 需要登录的请求携带 `Authorization: Bearer <JWT>`。管理员接口仅 ADMIN，购物车、订单和学生评价接口仅 STUDENT。
- 日期使用 `YYYY-MM-DD`。统计日期范围起止均包含。

## 健康检查和认证

| 方法 | 路径 | 权限 | 参数/用途 |
|---|---|---|---|
| GET | `/api/health` | 公开 | 服务健康检查 |
| POST | `/api/auth/register` | 公开 | JSON：`studentNo`、`password`、`nickname`；注册固定为 STUDENT |
| POST | `/api/auth/login` | 公开 | JSON：`studentNo`、`password`；返回 JWT 和用户资料 |
| GET | `/api/auth/me` | 已登录 | 返回当前令牌对应用户资料 |
| PUT | `/api/users/me` | 已登录 | JSON：`nickname`；只修改当前用户昵称 |

## 学生和公开接口

| 方法 | 路径 | 权限 | 查询参数/请求体 |
|---|---|---|---|
| GET | `/api/categories` | 公开 | 返回启用且未删除分类 |
| GET | `/api/dishes` | 公开 | 查询参数：`page`、`size`、`categoryId`、`keyword`、`sort`；`sort` 支持 `priceAsc`、`priceDesc`、`latest` |
| GET | `/api/dishes/hot` | 公开 | 返回已完成订单销量前 10 的在售菜品 |
| GET | `/api/dishes/{id}` | 公开 | 菜品详情 |
| GET | `/api/reviews/dish/{dishId}` | 公开 | 查询参数：`page`、`size`；仅返回审核通过评价 |
| GET | `/api/cart` | STUDENT | 查询当前学生购物车 |
| POST | `/api/cart/items` | STUDENT | JSON：`dishId`、`quantity` |
| PUT | `/api/cart/items/{dishId}` | STUDENT | JSON：`quantity`；修改本人购物车中该菜品数量 |
| DELETE | `/api/cart/items/{dishId}` | STUDENT | 删除本人购物车中该菜品 |
| POST | `/api/orders` | STUDENT | 必须带 `Idempotency-Key` 请求头；JSON：`pickupDate`、`pickupSlot`（LUNCH/DINNER）、可选 `remark` |
| GET | `/api/orders` | STUDENT | 查询参数：`page`、`size`、可选 `status`（PENDING/PREPARING/READY/COMPLETED/CANCELLED）；只查询本人订单 |
| GET | `/api/orders/{id}` | STUDENT | 本人订单详情和明细快照 |
| PUT | `/api/orders/{id}/cancel` | STUDENT | 仅本人 PENDING 订单可取消 |
| POST | `/api/orders/{id}/confirm-pickup` | STUDENT | 本人 READY 订单确认取餐 |
| POST | `/api/reviews` | STUDENT | JSON：`orderId`、`orderItemId`、`dishId`、`rating`、可选 `content` |
| PUT | `/api/reviews/{id}` | STUDENT | JSON：`rating`、可选 `content`；仅本人且符合完成后 30 天规则的评价可修改，修改后回到 PENDING |
| GET | `/api/reviews/my` | STUDENT | 查询参数：`page`、`size`；本人评价 |

订单金额和库存由后端根据数据库重新校验；客户端不能提交用户 ID、价格或总金额。

## 管理员接口

所有下列接口均要求 ADMIN。

| 方法 | 路径 | 查询参数/请求体 |
|---|---|---|
| GET | `/api/admin/categories` | 分类列表（含启用/停用状态） |
| POST | `/api/admin/categories` | JSON：`name`、`sortOrder`；新分类默认启用 |
| PUT | `/api/admin/categories/{id}` | JSON：`name`、`sortOrder` |
| PATCH | `/api/admin/categories/{id}/status` | JSON：`status`（1 启用，0 停用） |
| GET | `/api/admin/dishes` | 查询参数：`page`、`size`、`categoryId`、`keyword`、`sort`、`status` |
| POST | `/api/admin/dishes` | JSON：`categoryId`、`name`、`description`、`imageUrl`、`price`、`stock` |
| GET | `/api/admin/dishes/{id}` | 管理员菜品详情 |
| PUT | `/api/admin/dishes/{id}` | JSON：基本资料 `categoryId`、`name`、`description`、`imageUrl` |
| PATCH | `/api/admin/dishes/{id}/status` | JSON：`status`（ON_SALE/OFF_SALE） |
| PATCH | `/api/admin/dishes/{id}/stock` | JSON：`stock` |
| PATCH | `/api/admin/dishes/{id}/price` | JSON：`price` |
| DELETE | `/api/admin/dishes/{id}` | 逻辑删除菜品 |
| GET | `/api/admin/orders` | 查询参数：`page`、`size`、可选 `status` |
| GET | `/api/admin/orders/{id}` | 订单及商品快照详情 |
| POST | `/api/admin/orders/{id}/confirm` | PENDING → PREPARING |
| POST | `/api/admin/orders/{id}/ready` | PREPARING → READY |
| POST | `/api/admin/orders/{id}/complete` | READY → COMPLETED |
| POST | `/api/admin/orders/{id}/cancel` | 取消 PENDING、PREPARING 或 READY 订单并回补库存 |
| GET | `/api/admin/users` | 查询参数：`page`、`size`；不返回密码或密码哈希 |
| PATCH | `/api/admin/users/{id}/status` | JSON：`status`（1 启用，0 停用）；不能停用当前管理员 |
| GET | `/api/admin/reviews` | 查询参数：`page`、`size`、可选 `status` |
| PUT | `/api/admin/reviews/{id}/audit` | JSON：`status`（APPROVED/HIDDEN） |
| GET | `/api/admin/statistics/summary` | 必填查询参数：`startDate`、`endDate`；返回订单数、完成订单数和完成订单销售额 |
| GET | `/api/admin/statistics/dishes` | 必填查询参数：`startDate`、`endDate`；按完成时间汇总菜品销量 |

管理员 ID 从 JWT 取得，不从请求体或查询参数接收。

## 日期统计口径

订单总数和完成订单数按订单 `created_at` 落在日期范围内统计；完成销售额和菜品销量按 `completed_at` 落在日期范围内统计。日期起止均包含。只统计 COMPLETED 订单的销售额和销量。

用户被管理员停用后，新的登录会被拒绝；此前签发的无状态 JWT 在到期前仍可能有效。本项目当前不提供令牌黑名单或即时撤销机制。

## 错误响应

参数错误返回 400，未认证返回 401，无权限返回 403，资源不存在或不可见返回 404，业务状态冲突返回 409；客户端错误响应不包含堆栈、SQL 或凭据。
