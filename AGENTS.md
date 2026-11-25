# 项目开发规范

- 面向个人课程设计，先阅读 `docs/`；不擅自引入微服务、消息队列、缓存或容器编排。
- 前端 Vue 3/Vite/Element Plus/Axios/Vue Router；后端 Java 17/Spring Boot 3/Maven/MyBatis-Plus；MySQL 8。
- 中文说明，命名遵循语言惯例。后端按业务领域组织，Controller、Service、Mapper、Entity 职责清楚；DTO/VO 与 Entity 分开，避免过度抽象。
- Controller 负责 HTTP 和输入校验；Service 实现业务规则及事务；Mapper 负责持久化。使用 Bean Validation 和统一异常处理。
- API 统一 `{code,message,data}`；分页字段统一 `records,total,page,size`。不得向客户端返回堆栈、SQL 或秘密。
- 密码使用 BCrypt 等单向哈希；JWT 密钥、数据库凭据从环境变量或本地未提交配置读取。禁止提交真实秘密。
- JWT 认证和角色授权必须在后端执行；前端路由守卫只是体验措施。学生只能操作本人资源。
- 金额使用 `DECIMAL`/`BigDecimal`。订单金额由后端依据当前数据库价格计算，保留订单菜名和价格快照。
- 下单、库存扣减、订单取消及库存恢复放在事务内；并发扣库存使用带 `stock >= quantity` 条件的原子更新。状态迁移必须检查当前状态，保证不会重复回补库存。
- 数据库改动同步更新数据库设计及 ER 图。历史数据不物理删除，以停用/下架为主。
- 前端 Axios 统一处理令牌和错误；列表处理加载、空、失败状态；不得在客户端保存服务端秘密。
- 每阶段说明变更文件、验证方式和下一步建议；只报告实际执行的验证。修改前检查现有文件，避免覆盖既有工作。
