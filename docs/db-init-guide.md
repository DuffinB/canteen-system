# 数据库初始化脚本使用说明

## 执行前

确认 Navicat 已连接本机 MySQL 8.0.12，且 `canteen_system` 已创建并使用 utf8mb4。建议先备份该数据库。脚本只建表，不包含 `DROP DATABASE`、`DROP TABLE`、用户创建或密码数据。

## 使用 Navicat 执行

1. 在 Navicat 左侧选择连接下的 `canteen_system` 数据库，确认数据库字符集为 `utf8mb4`。
2. 打开该数据库的查询窗口，载入项目 `database/init.sql`（也可用文件打开后复制 SQL 到查询窗口）。
3. 确认当前查询窗口所属数据库是 `canteen_system`，然后执行全部脚本。
4. 查看执行消息；若遇到表已存在之外的错误，先停止后续操作并记录错误，不要删除现有数据来重试。

## 检查七张表

在 Navicat 刷新数据库节点，应看到 `users`、`categories`、`dishes`、`cart`、`orders`、`order_items`、`reviews`。也可执行：

```sql
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'canteen_system'
  AND TABLE_NAME IN ('users','categories','dishes','cart','orders','order_items','reviews')
ORDER BY TABLE_NAME;
```

结果应有七行，ENGINE 为 InnoDB，排序规则以 `utf8mb4` 开头。检查列、索引和外键可在 Navicat 表设计中查看，或运行 `SHOW CREATE TABLE users;`（将表名替换为其他表）。

## 重复执行说明

所有建表语句使用 `CREATE TABLE IF NOT EXISTS`：完整执行一次后再次执行不会删除或覆盖表中数据，已存在的表结构也不会自动修复/升级。因此它适合首次初始化和无变化时重复执行，不是迁移脚本。若部分表已存在，脚本会创建缺失表；已有表若结构不符，应先核对后单独制定迁移方案，不要直接删表。

`SET NAMES utf8mb4` 可安全重复执行。脚本没有测试数据插入语句，故没有重复插入数据问题。唯一键会拒绝重复学号、分类名、订单号、非空幂等键及同一订单明细重复评价；外键阻止删除被引用记录。MySQL 8.0.12 不强制 CHECK 约束，本设计依靠应用校验数量、评分、状态和库存非负。
