# 数据库设计

## 通用约定

MySQL 8/InnoDB/utf8mb4；BIGINT 自增主键；DATETIME 统一服务器时区（开发 Asia/Shanghai）；金额 DECIMAL(10,2)，Java 使用 BigDecimal。状态 VARCHAR，由应用校验。必要外键采取 RESTRICT/NO ACTION，避免级联删除历史订单。实体含 created_at、updated_at；用户/菜品/分类用逻辑停用。

## 表结构

### users

`id BIGINT PK`、`student_no VARCHAR(32) UNIQUE NOT NULL`、`password_hash VARCHAR(100) NOT NULL`、`nickname VARCHAR(50)`、`role VARCHAR(16)`（STUDENT/ADMIN）、`status TINYINT`、`deleted TINYINT`、`created_at`、`updated_at`。索引 `(role,status)`。注册固定 STUDENT。

### categories

`id BIGINT PK`、`name VARCHAR(50) UNIQUE`、`sort_order INT`、`status TINYINT`、`deleted TINYINT`、创建/更新时间。停用不删除被引用分类。名称唯一采用全局唯一，删除后不复用名称，避免软删除唯一键复杂化。

### dishes

`id BIGINT PK`、`category_id BIGINT FK categories.id`、`name VARCHAR(100)`、`description VARCHAR(1000)`、`image_url VARCHAR(500)`、`price DECIMAL(10,2)`、`stock INT`、`status VARCHAR(16)`（ON_SALE/OFF_SALE）、`deleted TINYINT`、创建/更新时间。索引 `(category_id,status,deleted)`、`(status,deleted,name)`。

### cart

`id BIGINT PK`、`user_id BIGINT FK users.id`、`dish_id BIGINT FK dishes.id`、`quantity INT > 0`、创建/更新时间。唯一键 `(user_id,dish_id)`，索引 `(user_id)`。不存价格。

### orders

`id BIGINT PK`、`order_no VARCHAR(40) UNIQUE`、`user_id BIGINT FK users.id`、`total_amount DECIMAL(10,2)`、`status VARCHAR(20)`（PENDING/PREPARING/READY/COMPLETED/CANCELLED）、`pickup_date DATE`、`pickup_slot VARCHAR(20)`（LUNCH/DINNER）、`remark VARCHAR(300)`、`idempotency_key VARCHAR(64) UNIQUE NULL`、`handled_by BIGINT NULL FK users.id`、`completed_at DATETIME NULL`、创建/更新时间。索引 `(user_id,created_at)`、`(status,created_at)`、`(pickup_date,pickup_slot,status)`。完成时间供准确统计。

### order_items

`id BIGINT PK`、`order_id BIGINT FK orders.id`、`dish_id BIGINT FK dishes.id`、`dish_name_snapshot VARCHAR(100)`、`unit_price DECIMAL(10,2)`、`quantity INT > 0`、`subtotal DECIMAL(10,2)`、创建时间。索引 `(order_id)`、`(dish_id)`。名称和单价快照保障历史展示，subtotal=单价×数量。

### reviews

`id BIGINT PK`、`user_id BIGINT FK users.id`、`dish_id BIGINT FK dishes.id`、`order_id BIGINT FK orders.id`、`order_item_id BIGINT FK order_items.id UNIQUE`、`rating TINYINT (1..5)`、`content VARCHAR(1000)`、`status VARCHAR(16)`（PENDING/APPROVED/REJECTED/HIDDEN）、`reviewed_by BIGINT NULL FK users.id`、`reviewed_at DATETIME NULL`、创建/更新时间。索引 `(dish_id,status,created_at)`、`(user_id,created_at)`、`(status,created_at)`。

## 关系、完整性和统计

用户拥有购物车、订单、评价；分类包含菜品；订单包含多条明细；评价必须属于同一用户的已完成订单明细且菜品一致，Service 校验，`UNIQUE(order_item_id)` 防重复。外键防误删，菜品下架代替删除。

销量与销售额仅计 `COMPLETED` 订单，分别汇总明细数量和 subtotal，按 `orders.completed_at` 做日期过滤。下单和取消库存处理采用事务及原子条件更新；服务端根据数据库当前价格计算金额。

订单号需随机/序列部分并有唯一索引；幂等键 NULL 允许多行，重试复用 UUID。数量、星级范围同时在 DTO 与服务层校验。
