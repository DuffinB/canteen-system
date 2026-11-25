-- 校园智慧食堂：MySQL 8.0.12 初始化脚本
-- 仅创建表，不删除数据库/用户数据。目标库 canteen_system 请先在 Navicat 中选中。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
  student_no VARCHAR(32) NOT NULL COMMENT '学号或管理员登录账号',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
  nickname VARCHAR(50) NULL COMMENT '昵称',
  role VARCHAR(16) NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT/ADMIN',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_student_no (student_no),
  KEY idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类主键',
  name VARCHAR(50) NOT NULL COMMENT '分类名称（全局唯一，不复用）',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品分类表';

CREATE TABLE IF NOT EXISTS dishes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜品主键',
  category_id BIGINT NOT NULL COMMENT '分类ID',
  name VARCHAR(100) NOT NULL COMMENT '菜品名称',
  description VARCHAR(1000) NULL COMMENT '菜品介绍',
  image_url VARCHAR(500) NULL COMMENT '图片地址',
  price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前单价（元）',
  stock INT NOT NULL DEFAULT 0 COMMENT '可售库存',
  status VARCHAR(16) NOT NULL DEFAULT 'OFF_SALE' COMMENT '状态：ON_SALE/OFF_SALE',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_dishes_category_status_deleted (category_id, status, deleted),
  KEY idx_dishes_status_deleted_name (status, deleted, name),
  CONSTRAINT fk_dishes_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品表';

CREATE TABLE IF NOT EXISTS cart (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '购物车主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  dish_id BIGINT NOT NULL COMMENT '菜品ID',
  quantity INT NOT NULL DEFAULT 1 COMMENT '数量，应用保证大于0',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cart_user_dish (user_id, dish_id),
  KEY idx_cart_user (user_id),
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_cart_dish FOREIGN KEY (dish_id) REFERENCES dishes (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单主键',
  order_no VARCHAR(40) NOT NULL COMMENT '订单编号',
  user_id BIGINT NOT NULL COMMENT '下单用户ID',
  total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '服务端计算订单总额（元）',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PREPARING/READY/COMPLETED/CANCELLED',
  pickup_date DATE NOT NULL COMMENT '预约取餐日期',
  pickup_slot VARCHAR(20) NOT NULL COMMENT '预约时段：LUNCH/DINNER',
  remark VARCHAR(300) NULL COMMENT '备注',
  idempotency_key VARCHAR(64) NULL COMMENT '下单幂等键',
  handled_by BIGINT NULL COMMENT '最近处理的管理员ID',
  completed_at DATETIME NULL COMMENT '完成取餐时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_order_no (order_no),
  UNIQUE KEY uk_orders_idempotency_key (idempotency_key),
  KEY idx_orders_user_created (user_id, created_at),
  KEY idx_orders_status_created (status, created_at),
  KEY idx_orders_pickup_status (pickup_date, pickup_slot, status),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_orders_handler FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单明细主键',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  dish_id BIGINT NOT NULL COMMENT '菜品ID',
  dish_name_snapshot VARCHAR(100) NOT NULL COMMENT '下单时菜品名称快照',
  unit_price DECIMAL(10,2) NOT NULL COMMENT '成交单价快照（元）',
  quantity INT NOT NULL COMMENT '购买数量',
  subtotal DECIMAL(10,2) NOT NULL COMMENT '明细成交金额快照（元）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_order_items_order (order_id),
  KEY idx_order_items_dish (dish_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_order_items_dish FOREIGN KEY (dish_id) REFERENCES dishes (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品明细表';

CREATE TABLE IF NOT EXISTS reviews (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价主键',
  user_id BIGINT NOT NULL COMMENT '评价用户ID',
  dish_id BIGINT NOT NULL COMMENT '评价菜品ID',
  order_id BIGINT NOT NULL COMMENT '关联订单ID',
  order_item_id BIGINT NOT NULL COMMENT '关联订单明细ID，每个明细最多一条评价',
  rating TINYINT NOT NULL COMMENT '评分1至5，应用校验',
  content VARCHAR(1000) NULL COMMENT '评价内容',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING/APPROVED/REJECTED/HIDDEN',
  reviewed_by BIGINT NULL COMMENT '审核管理员ID',
  reviewed_at DATETIME NULL COMMENT '审核时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reviews_order_item (order_item_id),
  KEY idx_reviews_dish_status_created (dish_id, status, created_at),
  KEY idx_reviews_user_created (user_id, created_at),
  KEY idx_reviews_status_created (status, created_at),
  CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_reviews_dish FOREIGN KEY (dish_id) REFERENCES dishes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品评价表';

-- 本脚本不创建管理员，不写入任何用户密码或密码哈希；测试分类/菜品可由 Navicat 手动添加。
