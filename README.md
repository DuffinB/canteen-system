# 校园智慧食堂

本仓库包含校园智慧食堂系统的课程设计文档、数据库脚本、Spring Boot 后端和 Vue 前端。本文说明本地数据库初始化和后端启动方式。

## 环境要求

- Windows 10/11
- JDK 17
- MySQL 8（项目按 MySQL 8.0.12 兼容性编写）
- IntelliJ IDEA（可选）
- 后端使用 Maven Wrapper，Maven 3.9.9；无需单独安装 Maven

## 初始化数据库

1. 在 Navicat 或 MySQL 客户端创建 `canteen_system` 数据库，字符集使用 `utf8mb4`。
2. 在 Navicat 中选中该数据库，打开并执行 [`database/init.sql`](database/init.sql)。脚本只建表，不创建管理员账号，也不写入密码。
3. `init.sql` 不创建管理员；当前项目没有管理员初始化或创建接口。需要管理员测试账号时，只能通过本地受控数据库操作写入已生成的 BCrypt 哈希，不能保存或插入明文密码。

Navicat 的完整操作及检查七张表的方法见 [`docs/db-init-guide.md`](docs/db-init-guide.md)。

## 配置本地环境变量

`backend/src/main/resources/application.yml` 从环境变量读取数据库用户名、密码和 JWT 密钥。不要把真实凭据写入 Git 跟踪文件。

在 PowerShell 当前窗口配置：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/canteen_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "你的本地 MySQL 用户名"
$env:DB_PASSWORD = "你的本地 MySQL 密码"
$env:JWT_SECRET = "替换为仅本机使用的随机长密钥，至少 32 字节"
```

这些变量只在当前 PowerShell 窗口有效。也可以在 IDEA 的 Run Configuration → Environment variables 中设置同名变量。`DB_URL` 默认使用本机 3306 端口和 `canteen_system`，本机端口不同时可通过该变量覆盖。

## 编译、测试和启动

在 PowerShell 中运行：

```powershell
cd D:\Projects\canteen-system\backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

后端默认监听 `8080`。浏览器打开 `http://localhost:8080/api/health`，成功时应返回统一 JSON 响应及 `UP` 状态。

导入 IDEA 时选择 `backend/pom.xml` 作为 Maven 工程，并使用项目 JDK 17。Wrapper 根据 `backend/.mvn/wrapper/maven-wrapper.properties` 使用 Maven 3.9.9。

## 重要说明

- 学生注册固定创建 STUDENT 账号；管理员不能通过注册接口创建。
- 订单金额和库存以服务端数据库校验为准；项目不接入真实在线支付。
- 用户被停用后，新的登录会被拒绝。已签发的无状态 JWT 在过期前仍可能有效；当前不实现令牌黑名单或即时撤销。
- API 路径、请求参数和状态规则见 [`docs/api-design.md`](docs/api-design.md)。
