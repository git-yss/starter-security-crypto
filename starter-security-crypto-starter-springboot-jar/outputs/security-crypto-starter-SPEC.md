# security-crypto-starter 项目规格说明

## 0. 文档目的

本文档用于直接提供给 AI 代码生成工具，生成一个可运行、可测试、可扩展的 Spring Boot 安全 Starter 项目。

项目名称：`security-crypto-starter`

项目定位：面向政企、密评、等保 2.0 和安全开发岗位展示的注解驱动型应用层安全组件。组件应尽量低侵入，通过 Spring Boot 自动装配、注解、拦截器、SPI 和配置完成敏感数据保护及安全审计。

本项目必须优先保证：

1. 可以作为 Jar 被其他 Spring Boot 项目引入。
2. 未启用的能力不应强制要求额外中间件。
3. 默认安全失败，不静默返回明文或密文。
4. 所有关键安全行为有结构化审计事件。
5. 使用 JDK 8 编译和运行。
6. 提供一个完整 Demo，展示主要功能的实际效果。

---

## 1. 技术基线

### 1.1 必须版本

- JDK：8
- Maven：3.8+
- Spring Boot：2.7.x
- Spring Framework：5.3.x
- Jackson：使用 Spring Boot 管理版本
- MyBatis：3.x
- MyBatis-Spring-Boot-Starter：2.x
- Redis：Spring Data Redis，生产使用 Redis
- JSON 日志：优先使用 Jackson 生成结构化 JSON
- 密码库：优先使用 Bouncy Castle，封装在 `CryptoProvider` 后面

### 1.2 JDK 8 限制

- 不得使用 Java 9 及以上 API。
- 不得使用 `var`、文本块、记录类、密封类、模式匹配等语法。
- 不得使用 `jakarta.*` 包。
- 使用 `javax.servlet.*`。
- 时间类型可以使用 `java.time`。
- 代码应兼容 Windows、Linux 和 Docker 环境。

### 1.3 Spring Boot 3 兼容边界

第一版只要求 Spring Boot 2.7 + JDK 8 可运行。

代码设计时不得把 Boot 2.7 专属实现散落在核心模块中。未来可以通过单独的 Boot 3 适配模块支持：

- Boot 3
- JDK 17+
- `jakarta.servlet.*`
- `AutoConfiguration.imports`

---

## 2. 功能边界

### 2.1 本组件负责

- 应用层敏感字段脱敏
- 数据库字段透明加密和解密
- HTTP 请求体和响应体加解密
- HTTP 请求签名和验签
- 防重放攻击
- 统一密码算法抽象
- 密钥来源抽象
- 密钥版本和密钥轮换基础能力
- 结构化安全审计
- 安全异常和统一响应
- 日志敏感信息脱敏
- 安全指标和健康检查

### 2.2 本组件不负责

- HTTPS、TLS、证书部署和传输层加密
- 登录认证
- Spring Security 或 Sa-Token 的认证实现
- RBAC 和权限管理
- 数据库 TDE
- WAF、防火墙和主机安全
- 自动判断真实操作员身份
- 替代 KMS、密码机或硬件安全模块
- 业务级审批流

### 2.3 操作员信息边界

组件不得自行猜测登录用户。组件提供扩展接口，由业务系统提供：

- `operatorId`
- `tenantId`
- `clientId`
- `deviceId`
- `channel`

如果业务未提供，审计字段可以为空，但不得写入伪造账号。

---

## 3. 项目结构

采用 Maven 多模块结构：

```text
security-crypto-starter/
├── pom.xml
├── security-crypto-core/
│   ├── crypto abstraction
│   ├── key abstraction
│   ├── masking abstraction
│   ├── audit abstraction
│   ├── context abstraction
│   └── common models and exceptions
├── security-crypto-autoconfigure/
│   ├── properties
│   ├── auto configuration
│   ├── web filters/interceptors
│   └── default implementations
├── security-crypto-mybatis/
│   ├── EncryptField support
│   ├── MyBatis TypeHandler
│   └── MyBatis interceptor
├── security-crypto-web/
│   ├── Sensitive Jackson module
│   ├── ApiCrypto support
│   ├── ApiSign support
│   └── replay protection
├── security-crypto-audit/
│   ├── structured audit
│   ├── JSON logger
│   └── audit publisher SPI
├── security-crypto-starter/
│   └── dependency aggregation module
├── security-crypto-demo/
│   ├── demo controller
│   ├── demo entity and mapper
│   ├── demo configuration
│   └── README
└── README.md
```

### 3.1 模块依赖规则

- `core` 不得依赖 Spring MVC、Redis、MyBatis。
- `core` 只允许依赖必要的基础库和密码库接口。
- `autoconfigure` 依赖 Spring Boot。
- `mybatis` 只有在引入 MyBatis 时才自动启用。
- `web` 只有在引入 Spring MVC 时才自动启用。
- Redis 只有启用防重放或 Redis KeyProvider 时才需要。
- Demo 可以依赖完整 Starter，但不能把 Demo 代码放入生产模块。

---

## 4. 自动装配要求

### 4.1 Starter 引入方式

业务项目只需要：

```xml
<dependency>
    <groupId>com.example.security</groupId>
    <artifactId>security-crypto-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 4.2 自动装配条件

必须使用以下条件控制：

- `@ConditionalOnClass`
- `@ConditionalOnMissingBean`
- `@ConditionalOnProperty`
- `@ConditionalOnWebApplication`

每个功能均提供独立开关。未启用的模块不得创建无用 Bean。

### 4.3 自动装配配置文件

Spring Boot 2.7 必须提供：

```text
META-INF/spring.factories
```

内容示例：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.example.security.autoconfigure.SecurityCryptoAutoConfiguration
```

### 4.4 启动检查

启动时检查：

- 是否配置了不安全的固定 IV。
- 生产环境是否使用本地明文密钥。
- 启用 Redis 防重放但没有 Redis Bean 时给出明确错误。
- 启用 API 签名但没有 KeyProvider 时给出明确错误。
- 是否启用了不安全的明文兼容模式。

生产环境安全配置错误时应阻止启动，而不是静默降级。

---

## 5. 全局配置

配置前缀统一为：

```yaml
security:
  crypto:
```

完整示例：

```yaml
security:
  crypto:
    enabled: true
    environment: dev
    fail-fast: true
    default-algorithm: SM4
    provider: bouncycastle
    max-body-size: 1048576
    plaintext-log-enabled: false

  masking:
    enabled: true
    default-strategy: DEFAULT
    null-safe: true
    collection-enabled: true
    fail-on-error: true

  field-encrypt:
    enabled: false
    prefix: SC1
    algorithm: SM4_GCM
    decrypt-failure-strategy: FAIL
    legacy-plaintext-enabled: false
    fail-on-unknown-ciphertext: true

  api-crypto:
    enabled: false
    request-field: data
    response-field: data
    algorithm: SM4_GCM
    max-body-size: 1048576

  api-sign:
    enabled: false
    algorithm: SM2_SM3
    include-method: true
    include-path: true
    include-query: true
    include-body: true
    timestamp-header: X-Security-Timestamp
    nonce-header: X-Security-Nonce
    signature-header: X-Security-Signature
    client-id-header: X-Security-Client-Id
    key-version-header: X-Security-Key-Version

  replay:
    enabled: false
    window-seconds: 300
    nonce-key-prefix: security:replay:
    fail-closed: true

  audit:
    enabled: true
    async: true
    record-success: false
    fail-on-publish-error: false
    topic: security-audit

  key:
    provider: local
    version: V1
    local:
      sm4-key-base64: ""
      sm2-public-key-base64: ""
      sm2-private-key-base64: ""
```

### 5.1 配置安全要求

- 本地密钥只允许开发和测试环境使用。
- 生产环境禁止在 YAML 中配置明文密钥。
- 密钥不得打印到启动日志。
- 配置类使用 `@ConfigurationProperties`。
- 敏感配置字段使用 `toString` 时必须隐藏。
- 建议支持环境变量覆盖。

---

## 6. 功能一：敏感字段脱敏 `@Sensitive`

### 6.1 功能说明

在 Jackson 序列化响应 JSON 时自动脱敏，不修改数据库值，不修改业务对象中的原始值。

### 6.2 适用场景

- 用户详情接口返回身份证号
- 员工信息接口返回手机号和邮箱
- 订单接口返回收货地址
- 管理后台返回银行卡号
- 审计或日志输出用户信息

### 6.3 内置策略

至少实现：

| 策略 | 示例 |
|---|---|
| `NAME` | `杨顺` -> `杨*` |
| `ID_CARD` | `510101199012345678` -> `5101********5678` |
| `PHONE` | `13812345678` -> `138****5678` |
| `BANK_CARD` | `6222021234567890123` -> `6222*********0123` |
| `EMAIL` | `test@163.com` -> `t***@163.com` |
| `ADDRESS` | `四川省成都市武侯区xxx` -> `四川省成都市武侯区***` |
| `CUSTOM` | 由业务 SPI 提供 |

### 6.4 注解设计

```java
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    MaskType type() default MaskType.DEFAULT;

    String strategy() default "";

    boolean ignore() default false;
}
```

使用示例：

```java
public class UserVO {

    private String name;

    @Sensitive(type = MaskType.PHONE)
    private String phone;

    @Sensitive(type = MaskType.ID_CARD)
    private String idCard;
}
```

类级别使用：

```java
@Sensitive(type = MaskType.PHONE)
public class ContactVO {
    private String phone;
}
```

### 6.5 自定义脱敏

提供：

```java
public interface Masker {

    String name();

    String mask(String value, MaskContext context);
}
```

业务通过 Spring Bean 注册：

```java
@Component
public class AccountMasker implements Masker {

    @Override
    public String name() {
        return "ACCOUNT";
    }

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****"
                + value.substring(value.length() - 2);
    }
}
```

### 6.6 处理规则

- 支持普通对象、嵌套对象、数组、Collection、Map。
- 默认只影响 JSON 序列化。
- 默认空值安全处理。
- 默认脱敏失败时拒绝序列化并产生审计事件。
- 不允许通过脱敏功能修改持久化数据。
- 应防止循环引用导致无限递归。

---

## 7. 功能二：数据库字段透明加密 `@EncryptField`

### 7.1 功能说明

业务实体字段标记后，写入数据库时自动加密，查询映射时自动解密。

### 7.2 适用场景

- 身份证号
- 手机号
- 银行卡号
- 家庭住址
- 医疗或政务敏感信息
- 不希望业务层感知密文的数据表字段

### 7.3 注解设计

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptField {

    String algorithm() default "SM4_GCM";

    String keyVersion() default "";

    boolean searchable() default false;
}
```

### 7.4 使用示例

```java
public class UserEntity {

    private Long id;

    @EncryptField(algorithm = "SM4_GCM")
    private String idCard;

    @EncryptField(algorithm = "SM4_GCM")
    private String phone;
}
```

### 7.5 实现要求

优先实现：

1. MyBatis `TypeHandler`，适用于明确的字段映射。
2. MyBatis Interceptor，作为复杂参数和结果场景的补充。

至少覆盖：

- INSERT
- UPDATE
- SELECT
- 单对象
- Collection
- 批量参数
- 常见 DTO 映射
- 空值
- 已经是密文的值

### 7.6 密文格式

密文必须带格式头、算法、密钥版本和随机参数。推荐格式：

```text
SC1$SM4_GCM$V1$BASE64_IV$BASE64_CIPHERTEXT$BASE64_TAG
```

具体实现可以将认证标签合并到密文尾部，但必须能够区分：

- 本组件密文
- 其他版本密文
- 普通明文
- 非法密文

### 7.7 安全规则

- 禁止 ECB。
- 禁止固定 IV。
- 每次加密必须生成新的随机 IV 或 nonce。
- 必须提供完整性校验。
- 解密失败默认抛出安全异常。
- 未知密文格式默认拒绝。
- 生产默认禁止明文兼容模式。
- 不得将原始明文写入日志或审计。

### 7.8 旧数据迁移兼容

允许显式开启：

```yaml
security:
  crypto:
    field-encrypt:
      legacy-plaintext-enabled: true
```

该模式只允许迁移期间临时使用，并必须：

- 打印高风险启动警告
- 写入审计事件
- 支持读明文、写密文
- 提供迁移工具或迁移脚本
- 生产环境默认关闭

---

## 8. 功能三：接口请求和响应加解密 `@ApiCrypto`

### 8.1 功能说明

对 HTTP JSON Body 进行应用层加解密，适用于登录、内部敏感接口和跨系统接口。

该功能不能替代 HTTPS，必须与 HTTPS 叠加使用。

### 8.2 模式

```java
public enum ApiCryptoMode {
    REQ_AND_RSP,
    REQ_ONLY,
    RSP_ONLY,
    NONE
}
```

### 8.3 注解设计

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiCrypto {

    ApiCryptoMode mode() default ApiCryptoMode.REQ_AND_RSP;

    String algorithm() default "SM4_GCM";

    String requestField() default "data";

    String responseField() default "data";
}
```

### 8.4 使用示例

```java
@RestController
@RequestMapping("/secure/user")
public class SecureUserController {

    @ApiCrypto(mode = ApiCryptoMode.REQ_AND_RSP)
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return loginService.login(request);
    }
}
```

请求外层格式：

```json
{
  "data": "SC1$SM4_GCM$V1$..."
}
```

### 8.5 处理流程

```text
读取请求体
-> 限制 Body 大小
-> 解析外层 JSON
-> 解密 data
-> 恢复原始 JSON
-> 进入 Controller
-> 获取业务响应
-> 序列化响应
-> 加密响应 data
-> 返回客户端
```

### 8.6 实现要求

- 使用 `ContentCachingRequestWrapper` 或等效机制解决 Body 只能读取一次问题。
- 默认只处理 `application/json`。
- 文件上传和 Multipart 默认不处理。
- 请求或响应超过限制时拒绝。
- 加解密异常统一转安全错误码。
- 异常响应也要遵循统一响应格式。
- 禁止把请求明文写入日志。
- 需要支持路径白名单和注解覆盖。

---

## 9. 功能四：请求签名和验签 `@ApiSign`

### 9.1 功能说明

防止请求参数、路径、Body 在传输过程中被篡改。

### 9.2 适用场景

- 支付或金额变更接口
- 政务跨系统调用
- 内部微服务调用
- 管理操作接口
- 客户端请求防篡改

### 9.3 注解设计

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiSign {

    boolean includeMethod() default true;

    boolean includePath() default true;

    boolean includeQuery() default true;

    boolean includeBody() default true;

    boolean replayCheck() default true;
}
```

### 9.4 请求头

```http
X-Security-Client-Id: client-a
X-Security-Timestamp: 1760000000000
X-Security-Nonce: 7e9b...
X-Security-Key-Version: V1
X-Security-Algorithm: SM2_SM3
X-Security-Signature: Base64Signature
```

### 9.5 签名原文规范

签名原文必须固定、可跨语言实现：

```text
METHOD\n
PATH\n
CANONICAL_QUERY\n
BODY_SHA256_OR_SM3\n
TIMESTAMP\n
NONCE\n
CLIENT_ID\n
KEY_VERSION
```

必须定义：

- HTTP 方法大写
- Path 是否 URL 解码
- Query 参数排序
- 重复参数处理
- 空值处理
- JSON 是否规范化
- 字符集固定为 UTF-8
- 换行符固定为 `\n`
- 签名编码固定为 Base64
- 错误时不得进入业务逻辑

### 9.6 验签流程

```text
读取请求元数据
-> 校验时间戳
-> 校验 nonce
-> 生成规范化签名原文
-> KeyProvider 获取客户端公钥
-> 验签
-> 通过后进入 Controller
```

签名失败必须：

- 返回统一错误码
- 产生 `SIGN_VERIFY_FAIL`
- 不打印签名密钥
- 不进入业务代码

---

## 10. 功能五：防重放攻击

### 10.1 功能说明

通过时间戳和一次性 nonce 防止攻击者重复提交合法请求。

### 10.2 配置

```yaml
security:
  crypto:
    replay:
      enabled: true
      window-seconds: 300
      fail-closed: true
      nonce-key-prefix: security:replay:
```

### 10.3 Redis 要求

必须使用 Redis 原子命令语义：

```text
SET key value NX EX 300
```

禁止使用先 `GET` 再 `SET` 的非原子实现。

### 10.4 校验规则

- 时间戳使用毫秒。
- 默认允许前后 300 秒偏差。
- nonce 最少 16 个随机字节对应的安全编码。
- nonce 应绑定 `clientId`。
- 同一个 nonce 第二次出现必须拒绝。
- Redis 不可用时，生产默认拒绝请求。
- 本地缓存降级只能显式配置，并仅建议测试环境使用。

---

## 11. 功能六：统一密码算法抽象 `CryptoProvider`

### 11.1 接口要求

```java
public interface CryptoProvider {

    String encrypt(String algorithm, byte[] plaintext,
                   CryptoContext context);

    byte[] decrypt(String algorithm, String ciphertext,
                   CryptoContext context);

    byte[] digest(String algorithm, byte[] data);

    String sign(String algorithm, byte[] data, KeyReference keyReference);

    boolean verify(String algorithm, byte[] data, String signature,
                   KeyReference keyReference);
}
```

### 11.2 内置算法

第一版至少支持：

- SM3
- SM4
- SM2 签名验签
- SM2 密钥封装或小数据加解密
- AES-GCM
- RSA 签名验签

大报文不得使用 SM2 直接加密。应使用混合加密：

```text
SM2 封装会话密钥 + SM4 加密业务数据
```

### 11.3 SPI 要求

- 算法通过接口调用，不得散落硬编码。
- 支持多个 Provider 实现。
- 相同算法名重复注册时启动失败。
- Provider 不可用时产生明确错误。
- 每次加解密使用 `SecureRandom`。
- 不得使用固定密钥和固定 IV。

---

## 12. 功能七：密钥管理 `KeyProvider`

### 12.1 接口要求

```java
public interface KeyProvider {

    KeyMaterial getKey(KeyRequest request);

    void refresh(KeyRefreshRequest request);

    List<String> supportedVersions(String keyName);
}
```

### 12.2 内置实现

1. `LocalKeyProvider`
   - 只用于开发和测试。
2. `EnvironmentKeyProvider`
   - 从环境变量或外部配置获取。
3. `KmsKeyProvider`
   - 通过 HTTP 或 SDK 接入外部 KMS。
4. `LayeredKeyProvider`
   - 支持 MK + DEK。

### 12.3 MK + DEK

- MK 只保存在 KMS、密码机或 HSM。
- DEK 用于实际业务字段加密。
- DEK 由 MK 加密保护后下发。
- 应用只缓存必要的短期 DEK。
- DEK 必须带版本。
- 密钥明文不得进入日志、审计和异常消息。

### 12.4 密钥轮换

必须支持：

- 新数据使用新版本密钥。
- 读取旧数据仍可根据密文版本解密。
- 旧版本密钥只读保留。
- 提供数据重加密工具的接口设计。
- 密钥轮换产生 `KEY_REFRESH` 审计事件。

---

## 13. 功能八：结构化安全审计

### 13.1 审计事件

至少支持：

```text
FIELD_ENCRYPT_SUCCESS
FIELD_ENCRYPT_FAIL
FIELD_DECRYPT_SUCCESS
FIELD_DECRYPT_FAIL
API_REQ_DECRYPT_SUCCESS
API_REQ_DECRYPT_FAIL
API_RSP_ENCRYPT_SUCCESS
API_RSP_ENCRYPT_FAIL
SIGN_VERIFY_SUCCESS
SIGN_VERIFY_FAIL
REPLAY_ATTACK_REJECT
KEY_OBTAIN_SUCCESS
KEY_OBTAIN_FAIL
KEY_REFRESH
SENSITIVE_MASK_SKIP
SECURITY_CONFIG_ERROR
```

### 13.2 审计对象

```java
public class SecurityAuditEvent {

    private String auditId;
    private String traceId;
    private String requestId;
    private String eventType;
    private String level;
    private boolean success;
    private long timestamp;
    private String serviceName;
    private String environment;
    private String operatorId;
    private String tenantId;
    private String clientId;
    private String requestUri;
    private String httpMethod;
    private String keyVersion;
    private String failureCode;
    private Long durationMs;
}
```

### 13.3 输出方式

默认：

- SLF4J JSON 结构化日志。

SPI 扩展：

- Kafka。
- RocketMQ。
- 独立审计库。
- 自定义回调。

### 13.4 审计安全要求

- 默认不记录成功事件，减少日志量。
- 失败事件必须记录。
- 不得记录明文请求体。
- 不得记录完整密钥、密码、Token。
- IP、手机号、身份证号按审计规则脱敏。
- 审计模块内部异常不得无限递归触发审计。
- 异步队列满时必须有明确丢弃或阻断策略。
- 高危事件可以配置为同步发布。

---

## 14. 功能九：统一异常和响应

### 14.1 错误码

建议至少支持：

```text
SEC-4001 INVALID_CIPHERTEXT
SEC-4002 DECRYPT_FAILED
SEC-4003 ENCRYPT_FAILED
SEC-4004 SIGNATURE_INVALID
SEC-4005 SIGNATURE_MISSING
SEC-4006 REQUEST_EXPIRED
SEC-4007 REPLAY_REQUEST
SEC-4008 KEY_UNAVAILABLE
SEC-4009 UNSUPPORTED_ALGORITHM
SEC-4010 BODY_TOO_LARGE
SEC-5001 SECURITY_PROVIDER_ERROR
```

### 14.2 统一响应示例

```json
{
  "success": false,
  "code": "SEC-4004",
  "message": "security validation failed",
  "traceId": "trace-123"
}
```

对外消息不得暴露：

- 密钥信息
- 堆栈信息
- 原始密文
- 解密细节
- 内部类名

内部日志可以记录异常类型，但不得记录敏感数据。

---

## 15. 功能十：日志脱敏 `@LogSensitive`

### 15.1 功能说明

对应用日志中的指定对象或字段进行脱敏，防止业务日志泄露敏感信息。

### 15.2 使用方式

```java
@LogSensitive
public void createUser(UserCommand command) {
    userService.create(command);
}
```

或者：

```java
public class UserCommand {

    @LogSensitive(type = MaskType.PHONE)
    private String phone;
}
```

### 15.3 边界

- 第一版优先支持方法参数对象序列化脱敏。
- 不要求修改第三方日志框架底层。
- 不建议通过全局字符串替换脱敏，因为可能误伤非敏感内容。
- 明文日志开关默认关闭。

---

## 16. 安全上下文

提供：

```java
public interface SecurityContextProvider {

    String getTraceId();

    String getRequestId();

    String getOperatorId();

    String getTenantId();

    String getClientId();

    String getDeviceId();

    String getChannel();
}
```

默认实现从 Servlet 请求和线程上下文获取；业务可通过 Spring Bean 覆盖。

不得在组件内部强依赖某一种登录框架。

---

## 17. 可观测性

使用 Micrometer 时提供以下指标：

```text
security_crypto_encrypt_total
security_crypto_decrypt_total
security_crypto_decrypt_failure_total
security_api_signature_verify_total
security_api_signature_failure_total
security_replay_reject_total
security_key_provider_request_total
security_key_provider_failure_total
security_audit_publish_failure_total
security_key_cache_hit_ratio
```

提供健康检查：

- Crypto Provider 是否可用。
- KeyProvider 是否可用。
- Redis 是否可用。
- 审计发布器是否可用。

健康检查不得泄露密钥和连接密码。

---

## 18. P2 可选功能

第一版可以预留接口，但不要求全部完成：

- 脱敏分组和环境分组。
- 接口白名单和黑名单。
- 存量数据迁移工具。
- 双写明文和密文。
- 盲索引。
- 确定性加密。
- 审计日志 SM3 摘要链。
- DEK 本地缓存。
- 高危事件告警回调。
- WebFlux 适配。
- JPA 适配。
- 消息队列消息签名。
- 文件加密。

确定性加密和盲索引必须在文档中标明：

> 会泄露相等关系，只适用于经过风险评估的精确匹配场景，不得默认开启。

---

## 19. Demo 项目要求

Demo 必须提供以下接口：

### 19.1 脱敏接口

```text
GET /demo/users/{id}
```

返回用户姓名、手机号、身份证号，JSON 中自动脱敏。

### 19.2 字段加密接口

```text
POST /demo/users
GET /demo/users/{id}
```

数据库中保存密文，业务返回对象中得到明文，响应 JSON 再脱敏。

### 19.3 API Body 加解密接口

```text
POST /demo/secure/login
```

演示请求体解密和响应体加密。

### 19.4 签名接口

```text
POST /demo/signed/order
```

演示：

- 合法签名成功
- 参数被篡改后验签失败
- 时间戳过期
- nonce 重复

### 19.5 审计查看

Demo 不得提供生产级审计查询接口。可以提供测试日志说明或内存审计 Publisher，便于单元测试验证事件。

---

## 20. 测试要求

### 20.1 单元测试

至少覆盖：

- SM3 摘要
- SM4 加解密
- SM2 签名验签
- 密文格式解析
- 随机 IV 不重复
- 脱敏策略
- 嵌套对象脱敏
- 集合脱敏
- 空值处理
- 失败策略
- 签名规范化
- 时间戳校验

### 20.2 集成测试

至少覆盖：

- Spring Boot 自动装配
- 未启用功能不创建 Bean
- MyBatis 插入自动加密
- MyBatis 查询自动解密
- API 请求体解密
- API 响应体加密
- Redis nonce 原子防重放
- 审计事件输出
- 统一异常响应

### 20.3 安全测试

至少覆盖：

- 修改密文导致认证标签校验失败
- 修改请求 Body 导致验签失败
- 重复 nonce 被拦截
- 过期时间戳被拦截
- 未知算法被拒绝
- 未知密文版本被拒绝
- Redis 不可用时生产 fail-closed
- 日志中不出现密钥和明文
- 并发场景下 nonce 只有一个请求成功

### 20.4 验收命令

```bash
mvn clean test
mvn clean package
java -jar security-crypto-demo/target/security-crypto-demo-1.0.0.jar
```

---

## 21. 代码质量要求

- 所有公共类、接口和配置项提供 JavaDoc。
- 所有安全默认值必须安全。
- 禁止空 catch。
- 禁止吞掉解密、验签和密钥获取异常。
- 不得在异常消息中拼接明文、密钥和完整 Token。
- 日志使用参数化写法。
- 线程池、缓存和连接资源必须可配置。
- 自动装配 Bean 必须有清晰命名。
- 关键行为必须可通过测试验证。
- README 必须包含快速开始、完整配置、API 示例和安全边界。

---

## 22. AI 生成任务要求

请基于本文档生成完整 Maven 多模块项目，要求：

1. 生成可编译的 JDK 8 项目。
2. 先生成核心接口、模型、异常和配置类，再生成实现。
3. 不得只生成空接口或伪代码。
4. 所有自动装配类必须可运行。
5. 必须提供 Demo、README、YAML 配置和测试。
6. 如果某个高级功能无法一次实现，必须保留清晰 SPI 接口并提供可运行的默认实现。
7. 禁止为了演示而硬编码生产密钥。
8. 本地 Demo 可以使用测试密钥，但必须明确标注仅限测试。
9. 生成过程中优先保证 P0 功能完整，再实现 P1 和 P2。
10. 每完成一个模块后运行测试，不得等到最后才验证。

### 推荐实现顺序

```text
1. Maven 父工程和模块
2. core 模型、异常、SPI
3. CryptoProvider 和本地 KeyProvider
4. SM3、SM4、SM2 实现
5. 自动装配和配置绑定
6. @Sensitive
7. @EncryptField 和 MyBatis 支持
8. @ApiSign 和防重放
9. @ApiCrypto
10. 审计
11. 统一异常
12. 指标和健康检查
13. Demo
14. 单元测试和集成测试
15. README 和安全说明
```

---

## 23. 最终验收标准

项目完成后必须满足：

- 可以使用一个 Starter 依赖接入 Demo。
- Demo 启动成功。
- 敏感字段在 JSON 响应中自动脱敏。
- 数据库中的敏感字段是密文。
- 查询时业务层可以获得明文。
- 修改密文后解密失败且不返回原始密文。
- API 签名正确时进入业务。
- 签名错误时不进入业务。
- nonce 重复时请求被拦截。
- 请求体和响应体可以按注解配置加解密。
- 密钥来源可通过 SPI 替换。
- 关键安全事件可以以结构化日志输出。
- 日志和审计中不出现密钥及敏感明文。
- 未启用 Redis 时，不使用防重放功能的项目仍可正常启动。
- `mvn clean test` 通过。
- README 能让另一个开发者在 10 分钟内完成 Demo 接入。

