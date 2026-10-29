# 青梅里 · 平台后端（backend-admin）

青梅里乡村文旅 SaaS 平台的 **统一后端服务**，同时为三端提供接口：用户端 H5 商城、商家端 H5、平台管理后台 PC。

## 技术栈

| 项目 | 说明 |
| --- | --- |
| 框架 | Spring Boot 3.2.5 / Java 17 |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | MySQL 8（库名 `qingmeili`） + Druid 连接池 |
| 缓存 | Redis（Spring Data Redis） |
| 鉴权 | JWT（jjwt 0.12.5） + Spring Security Crypto |
| AI 能力 | LangChain4j 1.22.0（Agent 编排 / 函数调用 / 结构化输出 / 对话记忆 / 向量检索） |
| 工具 | Hutool、Lombok、commons-lang3 |

## 包结构

```
src/main/java/com/qingmeili
├── QingmeiliApplication.java   # 启动类
├── controller/                 # 71 个控制器，按端分组
│   ├── admin/                  #   平台管理后台接口（27）
│   ├── merchant/               #   商家端接口（14）
│   └── api/                    #   用户端 C 端接口（24）
├── service/                    # 业务逻辑（63）
├── mapper/                     # MyBatis-Plus Mapper（47）
├── entity/                     # 数据实体（47）
├── dto/                        # 请求/响应对象（32）
├── ai/                         # AI 能力：对话、向量嵌入、天气工具等（18）
├── common/                     # 通用能力：统一返回、异常、注解、上下文、工具（16）
├── config/                     # 配置类：安全、跨域、拦截器、MP、Redis 等（8）
└── task/                       # 定时任务（3）
```

`src/main/resources` 下为 `application.yml` 与 `content` 内容资源；`static/`、`uploads/` 存放图片、音频等业务静态资源。

## 本地运行

前置：JDK 17、Maven 3.9+、MySQL 8、Redis。

1. 建库并导入脚本（同交付包 `数据库脚本/` 目录，按 `01` → `12` 顺序执行）。
2. 按需覆盖数据库连接（可用环境变量，默认值见 `application.yml`）：

```bash
DB_HOST=localhost
DB_PORT=3306
DB_USERNAME=root
DB_PASSWORD=123456
SERVER_PORT=8081
REDIS_HOST=localhost
REDIS_PORT=6379
```

3. 启动：

```bash
./mvnw spring-boot:run          # 或 mvn spring-boot:run
# 或打包
./mvnw clean package -DskipTests
java -jar target/qingmeili-backend-1.0.0.jar
```

服务默认监听 **http://localhost:8081**（本机 8080 常被其他服务占用，故整体使用 8081），三端前端 Vite 代理均指向此地址。

## 前端仓库

- 用户端 H5 商城：[dayi-saas-frontend-user](https://github.com/2qqqyy/dayi-saas-frontend-user)
- 商家端 H5：[dayi-saas-frontend-merchan-h5](https://github.com/2qqqyy/dayi-saas-frontend-merchan-h5)
- 管理后台 PC：[dayi-saas-frontend-admin](https://github.com/2qqqyy/dayi-saas-frontend-admin)
