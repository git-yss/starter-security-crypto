# security-crypto-starter

面向 JDK 8、Spring Boot 2.7 的应用层安全 Starter，提供字段加密、接口加解密、请求签名、防重放、JSON 脱敏和 HTTP 审计能力。

## 阅读导航

- 第一次学习项目：阅读 [`docs/LEARNING-GUIDE.md`](docs/LEARNING-GUIDE.md)，其中包含调用链、源码顺序和调试断点。
- 准备接入生产：参考 [`docs/application-production.yml`](docs/application-production.yml) 和本文的“生产环境接入”。
- 查看完整设计背景：阅读 [`outputs/security-crypto-starter-SPEC.md`](outputs/security-crypto-starter-SPEC.md)。该文档是设计目标，不代表所有规划能力均已实现，应以当前源码和测试为准。

## 功能概览

| 功能 | 使用入口 | 默认状态 | 核心实现 |
| --- | --- | --- | --- |
| JSON 字段脱敏 | `@Sensitive` | Starter 开启后可用 | Jackson `SensitiveModule` |
| 接口请求/响应加密 | `@ApiCrypto` | 关闭 | Spring MVC 请求/响应 Advice |
| 请求签名 | `@ApiSign` | 关闭 | MVC `HandlerInterceptor` |
| 防重放 | `@ApiSign(replayCheck = true)` | 关闭 | 内存或 Redis `ReplayStore` |
| MyBatis 字段加密 | `@EncryptField` | 关闭 | MyBatis `Interceptor` |
| HTTP 操作审计 | 全局 Filter | 开启 | `AuditFilter` + `AuditPublisher` |

## 模块与能力

- `core`：AES-GCM、HMAC-SHA256、密钥抽象、防重放和通用注解。
- `web`：`@ApiCrypto` 请求解密/响应加密、`@ApiSign` 验签、`@Sensitive` JSON 脱敏。
- `mybatis`：`@EncryptField` 入库前加密、查询后解密，并在 SQL 执行后恢复原参数。
- `audit`：记录操作人、客户端 IP、URI、脱敏后的查询参数和请求体、响应状态及耗时。
- `autoconfigure` / `starter`：自动装配和生产启动检查。

所有高风险能力默认按需开启。Starter 只处理加密、验签、脱敏与审计，不代替 HTTPS、身份认证、RBAC、数据权限和业务授权。

## 快速体验

需要 JDK 8 和 Maven 3.8+：

```bash
mvn clean package
java -jar demo/target/security-crypto-demo-1.0.0-SNAPSHOT.jar
```

启动成功后访问：

```text
GET http://localhost:8080/demo/users/1
GET http://localhost:8080/demo/patients/1/masked
GET http://localhost:8080/demo/patients/1/plain
```

PowerShell 示例：

```powershell
Invoke-RestMethod http://localhost:8080/demo/users/1
Invoke-RestMethod http://localhost:8080/demo/patients/1/masked
Invoke-RestMethod http://localhost:8080/demo/patients/1/plain
```

重点观察：

- `users/1` 中手机号和身份证号自动脱敏。
- `patients/1/masked` 返回脱敏 DTO。
- `patients/1/plain` 返回明文 DTO，仅用于演示，真实项目必须增加权限控制。
- 控制台中的 `SECURITY_AUDIT` 日志记录请求方法、URI、状态码和耗时。

`/demo/secure/login` 需要客户端先生成 AES-GCM 密文；`/demo/signed/order` 只有开启 `api-sign.enabled` 并按签名协议生成请求头后才能正常调用。具体调试方式见学习指南。

## 业务项目引入

当前版本为本地 `SNAPSHOT` 工程。先安装到本地 Maven 仓库：

```bash
mvn clean install
```

业务项目增加依赖：

```xml
<dependency>
    <groupId>com.example.security</groupId>
    <artifactId>security-crypto-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

然后根据需要开启功能并在业务类上使用对应注解。默认实现都可以通过声明同类型 Spring Bean 进行替换，例如 `KeyProvider`、`Masker`、`ReplayStore` 和 `AuditPublisher`。

MyBatis 字段加密模块是可选依赖。需要使用 `@EncryptField` 时，业务项目还要引入：

```xml
<dependency>
    <groupId>com.example.security</groupId>
    <artifactId>security-crypto-mybatis</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

同时需要业务项目自身已经集成 MyBatis。生产环境使用 Redis 防重放时，还要引入 `spring-boot-starter-data-redis` 并提供可用的 `StringRedisTemplate`。

## 开发环境配置

本地密钥值必须是 Base64 编码。下面的密钥仅用于开发测试，禁止用于生产：

```yaml
security:
  crypto:
    enabled: true
    environment: dev
    fail-fast: true
    key-version: V1
    max-body-size: 1048576
    key:
      local:
        V1: MTIzNDU2Nzg5MDEyMzQ1Ng==
    api-crypto:
      enabled: true
    api-sign:
      enabled: true
      window-seconds: 300
    replay:
      enabled: true
      store: memory
    audit:
      enabled: true
      allow-operator-header: false
      trust-forwarded-headers: false
      fail-on-publish-error: false
    field-encrypt:
      enabled: true
```

以上配置是为了同时演示全部能力。实际项目应只开启确实需要的功能，避免无意义地缓存所有请求体或扩大安全组件的影响范围。

## 生产环境接入

生产配置示例见 [`docs/application-production.yml`](docs/application-production.yml)。生产启动检查会拒绝以下配置：

- 使用默认本地 `KeyProvider`，除非显式设置 `key.allow-local-in-production=true`（不推荐）。
- 验签与防重放开启时使用内存防重放存储。
- 信任客户端传入的 `X-Operator-Id`。
- 缺少当前 `key-version` 对应密钥，或请求体、签名时间窗配置非法。

业务系统应提供 KMS、Vault 或 HSM 支持的 `KeyProvider`，不要把生产密钥写进 YAML、代码、镜像、日志或审计事件：

```java
@Bean
public KeyProvider kmsKeyProvider(KmsClient kmsClient) {
    return version -> kmsClient.getSymmetricKey(version);
}
```

生产防重放使用 Redis 原子 `SET NX` 与 TTL。应用需要提供可用的 `StringRedisTemplate`，并配置：

```yaml
security.crypto.replay.store: redis
```

Redis 应配置高可用、访问认证、TLS（环境支持时）、容量告警和 key 淘汰监控。

## 请求签名协议

客户端发送以下请求头：

```text
X-Security-Timestamp: Unix 毫秒时间戳
X-Security-Nonce: 16-128 字符随机串
X-Security-Client-Id: 客户端标识
X-Security-Key-Version: 密钥版本（可省略，省略时使用服务端当前版本）
X-Security-Signature: Base64(HMAC-SHA256(canonical))
```

`canonical` 按下列顺序拼接，每项之间只有一个换行符：

```text
HTTP_METHOD
REQUEST_URI
RAW_QUERY_STRING
BASE64(SHA-256(raw request body))
TIMESTAMP
NONCE
CLIENT_ID
KEY_VERSION
```

是否包含 method、path、query、body 由 `@ApiSign` 属性决定；不包含时对应行保留为空行。查询字符串使用原始顺序和原始编码，客户端与服务端必须完全一致。

注意：当前签名协议包含请求体摘要、客户端 ID 和密钥版本。若从旧版本升级，所有调用方必须同步更新签名实现，否则请求会被拒绝。

## 接口加密与字段加密

`@ApiCrypto` 的请求格式：

```json
{
  "data": "SC1$AES_GCM$V1$..."
}
```

密文自带密钥版本，解密时会按该版本选取密钥，便于滚动轮换。AES-GCM 不能替代 HTTPS；浏览器和客户端运行时仍可能接触明文。

MyBatis 实体字段可以使用：

```java
public class PatientEntity {
    @EncryptField
    private String phone;
}
```

复杂动态 SQL、Map/集合嵌套参数、批量写入、联表 DTO 和自定义 TypeHandler 仍需在业务项目中增加集成测试。

## 审计安全

- 默认不信任 `X-Operator-Id`，生产应实现 `OperatorContextProvider` 从认证上下文获取操作人。
- 默认不信任 `X-Forwarded-For`。只有应用部署在会清洗并重写该请求头的可信代理之后，才可开启 `trust-forwarded-headers`。
- 密码、token、authorization、证件号、手机号、地址、诊断等查询参数和 JSON 字段会被脱敏。
- `fail-on-publish-error=false` 时，审计发布失败记录错误日志但不影响业务；强合规系统可改为 `true`，并配套高可用审计存储。

## 上线前检查

上线仍需完成外部 KMS/Vault/HSM、Redis 高可用、密钥轮换与吊销、指标与告警、日志留存、依赖漏洞扫描、渗透测试、容量压测和灾备演练。本项目提供的是生产集成基线，不等同于经过认证的完整安全产品。

## 测试

```bash
mvn test
```

当前测试覆盖密钥版本选择、AES-GCM 加解密、未实现算法拒绝、防重放过期清理、请求正文验签、MyBatis 参数恢复、审计脱敏和生产启动保护。接入具体业务后，还应补充 Controller、数据库、Redis 和外部密钥系统的集成测试。
