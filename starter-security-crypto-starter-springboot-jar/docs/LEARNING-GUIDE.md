# security-crypto-starter 学习指南

这份文档用于回答三个问题：这个 Starter 有什么功能、业务项目如何使用、功能在 Spring Boot 内部如何实现。

## 1. 先建立整体模型

这个项目不是一个独立业务系统，而是一组可以被其他 Spring Boot 项目引入的安全基础能力。

```text
业务 Controller / MyBatis
        |
        v
注解：@Sensitive / @ApiCrypto / @ApiSign / @EncryptField
        |
        v
Web Advice、MVC Interceptor、Servlet Filter、MyBatis Interceptor
        |
        v
CryptoProvider / KeyProvider / ReplayStore / AuditPublisher / Masker
        |
        v
JCE、Redis、日志系统、KMS/Vault/HSM（业务扩展）
```

项目采用“注解声明行为 + Spring 自动装配 + 接口允许替换”的设计。业务代码只写注解，Starter 在框架扩展点中执行实际逻辑。

## 2. 模块职责

| 模块 | 职责 | 首要阅读文件 |
| --- | --- | --- |
| `core` | 注解、SPI 接口、AES-GCM、HMAC、脱敏、防重放基础实现 | `CryptoProvider`、`JceCryptoProvider` |
| `web` | 接口加解密、验签、请求体缓存、JSON 脱敏、异常响应 | `ApiCryptoAdvice`、`ApiSignInterceptor` |
| `audit` | 捕获 HTTP 请求并生成脱敏审计事件 | `AuditFilter` |
| `mybatis` | 数据写入前加密、查询结果解密 | `EncryptFieldInterceptor` |
| `autoconfigure` | 根据配置创建 Bean，提供默认实现和生产启动检查 | `SecurityCryptoAutoConfiguration` |
| `starter` | 聚合依赖，供业务项目一次性引入 | `starter/pom.xml` |
| `demo` | 展示注解和配置的使用方式 | `DemoController`、`PatientController` |

## 3. 推荐阅读顺序

不要按目录字母顺序阅读。按下面顺序可以最快形成调用链。

1. `demo/src/main/resources/application.yml`：先看哪些能力可以开关。
2. `DemoController`、`PatientController`：看四个注解如何出现在业务代码中。
3. `META-INF/spring.factories`：理解 Starter 为什么能自动生效。
4. `SecurityCryptoAutoConfiguration`：查看每个配置创建了什么 Bean。
5. `ApiCryptoAdvice`、`ApiSignInterceptor`、`SensitiveModule`：理解 Web 请求链。
6. `EncryptFieldInterceptor`：理解 MyBatis 写前加密、读后解密。
7. `AuditFilter`：理解请求审计和敏感数据清洗。
8. `JceCryptoProvider`：最后阅读底层密码学封装。
9. 对应测试类：通过输入和断言确认边界行为。

## 4. 自动装配是如何开始的

业务项目引入 `security-crypto-starter` 后，依赖会带入 `autoconfigure`。Spring Boot 2.7 启动时读取：

```text
autoconfigure/src/main/resources/META-INF/spring.factories
mybatis/src/main/resources/META-INF/spring.factories
```

随后加载 `SecurityCryptoAutoConfiguration`。该类根据 `security.crypto.*` 配置创建默认组件：

```text
KeyProvider          -> DefaultKeyProvider
CryptoProvider       -> JceCryptoProvider
Masker               -> DefaultMasker
ReplayStore          -> InMemoryReplayStore 或 RedisReplayStore
AuditPublisher       -> Slf4jAuditPublisher
OperatorContextProvider -> DefaultOperatorContextProvider
```

这些 Bean 大多使用 `@ConditionalOnMissingBean`。业务项目声明同类型 Bean 后，默认实现会退让，这是接入 KMS、定制脱敏和持久化审计的主要扩展方式。

## 5. 功能一：JSON 字段脱敏

### 使用

```java
public class UserResponse {
    @Sensitive(type = MaskType.PHONE)
    private String phone;

    @Sensitive(type = MaskType.ID_CARD)
    private String idCard;
}
```

Controller 正常返回对象即可，不需要手动调用脱敏方法。

### 原理

```text
Controller 返回对象
  -> Jackson 开始序列化
  -> SensitiveModule 检查字段上的 @Sensitive
  -> 为字段替换 MaskingSerializer
  -> MaskingSerializer 调用 Masker
  -> DefaultMasker 输出脱敏字符串
```

核心扩展点是 Jackson `BeanSerializerModifier`。脱敏只改变响应 JSON，不修改原 Java 对象。

建议断点：`SensitiveModule.changeProperties()`、`MaskingSerializer.serialize()`、`DefaultMasker.mask()`。

## 6. 功能二：接口请求/响应加密

### 使用

```java
@ApiCrypto
@PostMapping("/secure/login")
public LoginResponse login(@RequestBody LoginRequest request) {
    return service.login(request);
}
```

客户端发送：

```json
{
  "data": "SC1$AES_GCM$V1$BASE64_IV$BASE64_CIPHERTEXT_AND_TAG"
}
```

服务端响应同样包装在 `data` 字段中。可以通过 `ApiCryptoMode` 设置仅请求解密或仅响应加密。

### 原理

请求方向：

```text
HTTP 密文 JSON
  -> RequestBodyAdvice.beforeBodyRead()
  -> 读取 data 字段
  -> CryptoProvider.decrypt()
  -> 用明文 JSON 替换 HttpInputMessage body
  -> Jackson 转为 Controller 参数
```

响应方向：

```text
Controller 返回对象
  -> ResponseBodyAdvice.beforeBodyWrite()
  -> ObjectMapper 转 JSON
  -> CryptoProvider.encrypt()
  -> 返回 {"data":"密文"}
```

`JceCryptoProvider` 使用 AES-GCM。每次加密生成 12 字节随机 IV，密文中携带密钥版本，因此轮换密钥后仍可读取旧数据。

建议断点：`ApiCryptoAdvice.beforeBodyRead()`、`beforeBodyWrite()`、`JceCryptoProvider.encrypt/decrypt()`。

## 7. 功能三：接口签名与防重放

### 使用

```java
@ApiSign
@PostMapping("/orders")
public OrderResponse create(@RequestBody OrderRequest request) {
    return orderService.create(request);
}
```

客户端需要发送时间戳、随机数、客户端 ID、密钥版本和签名。签名原文为：

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

然后计算：

```text
Base64(HMAC-SHA256(canonical, clientKey))
```

### 原理

```text
SecurityRequestCachingFilter 缓存原始 body
  -> DispatcherServlet 找到 HandlerMethod
  -> ApiSignInterceptor.preHandle()
  -> 判断方法/类是否有 @ApiSign
  -> 检查请求头和时间窗口
  -> 生成 canonical 字符串
  -> HMAC 常量时间比较
  -> ReplayStore 原子登记 clientId + nonce
  -> 放行 Controller 或返回 401
```

Servlet 请求体默认只能读取一次，因此验签前必须使用 `CachedBodyHttpServletRequest` 保存原始字节。生产环境的防重放必须使用 Redis；`SET NX + TTL` 保证同一个 nonce 只成功一次。

建议断点：`SecurityRequestCachingFilter.doFilterInternal()`、`ApiSignInterceptor.preHandle()`、`canonical()`、`RedisReplayStore.useOnce()`。

## 8. 功能四：MyBatis 字段透明加密

### 使用

```java
public class PatientEntity {
    @EncryptField
    private String phone;
}
```

开启：

```yaml
security.crypto.field-encrypt.enabled: true
```

### 原理

`EncryptFieldInterceptor` 拦截两个 MyBatis 扩展点：

```text
ParameterHandler.setParameters()
  -> 递归查找 @EncryptField
  -> 临时将参数字段替换为密文
  -> MyBatis 写入 PreparedStatement
  -> finally 中恢复原 Java 对象的明文

ResultSetHandler.handleResultSets()
  -> MyBatis 完成结果映射
  -> 递归查找 @EncryptField
  -> 将密文替换为明文
  -> 返回业务层
```

使用 `IdentityHashMap` 是为了按对象身份去重，避免同一个对象被 Map 中多个键引用时重复加密或解密。写操作记录 `FieldChange` 并在 `finally` 中逆序恢复，避免业务对象在 SQL 执行后仍保留密文。

建议断点：`EncryptFieldInterceptor.intercept()`、`encryptForWrite()`、`processFields()`、`restore()`。

## 9. 功能五：HTTP 操作审计

### 原理

```text
AuditFilter 包装请求
  -> 业务 Filter/Controller 执行
  -> finally 构造 AuditEvent
  -> 查询参数和 JSON body 脱敏
  -> AuditPublisher.publish()
  -> 默认输出到 SECURITY_AUDIT logger
```

操作人默认来自 `OperatorContextProvider`。生产环境应实现该接口，从 Spring Security 或现有登录上下文获取用户 ID，而不是信任客户端请求头。

审计发布默认失败不影响业务；强合规系统可开启 `fail-on-publish-error`，但需要确保审计存储高可用。

建议断点：`AuditFilter.doFilterInternal()`、`safeBody()`、`Slf4jAuditPublisher.publish()`。

## 10. 密钥与可替换接口

开发环境可在 YAML 中配置 Base64 密钥。生产环境应替换 `KeyProvider`：

```java
@Bean
public KeyProvider keyProvider(KmsClient kmsClient) {
    return version -> kmsClient.loadKey(version);
}
```

推荐重点理解以下 SPI：

| 接口 | 替换场景 |
| --- | --- |
| `KeyProvider` | KMS、Vault、HSM、密钥轮换 |
| `CryptoProvider` | 国密或硬件密码机 |
| `ReplayStore` | Redis 集群或其他原子存储 |
| `AuditPublisher` | Kafka、数据库、审计平台 |
| `OperatorContextProvider` | Spring Security、JWT、企业登录上下文 |
| `Masker` | 企业自定义脱敏规范 |

## 11. 两小时学习实践

### 第 1 阶段：运行和观察（20 分钟）

1. 启动 `DemoApplication`。
2. 请求 `/demo/users/1`，观察 `@Sensitive` 输出。
3. 请求 `/demo/patients/1/masked` 和 `/plain`，比较 DTO 差异。
4. 查看 `SECURITY_AUDIT` 日志。

### 第 2 阶段：跟踪自动装配（25 分钟）

1. 在 `SecurityCryptoAutoConfiguration` 每个 Bean 方法打断点。
2. 重启应用，记录哪些配置控制哪些 Bean。
3. 自定义一个 `Masker` Bean，验证默认 Bean 自动退让。

### 第 3 阶段：跟踪请求链（35 分钟）

1. 为 `ApiCryptoAdvice` 和 `ApiSignInterceptor` 打断点。
2. 开启 `api-sign.enabled`，使用测试类中的签名方式构造请求。
3. 修改 body、nonce 或 timestamp，观察失败位置。

### 第 4 阶段：跟踪数据库链（20 分钟）

1. 阅读 `EncryptFieldInterceptorTest`。
2. 观察 SQL 执行前字段变密文、执行后恢复明文。
3. 增加集合或 Map 参数测试。

### 第 5 阶段：阅读测试和生产约束（20 分钟）

1. 运行 `mvn test`。
2. 阅读四个模块的测试类。
3. 修改 `environment: prod`，观察 `ProductionReadinessValidator` 拒绝不安全配置。

## 12. 当前边界

- Starter 不做登录认证、RBAC、数据权限或明文接口授权。
- 默认密码算法只有 AES-GCM、HMAC-SHA256 和 SHA-256，不包含真正的 SM2/SM3/SM4。
- MyBatis 复杂动态 SQL、批量参数、自定义 TypeHandler 需要业务集成测试。
- 接口加密不能替代 HTTPS。
- 本地密钥与内存防重放只适合开发环境。
- 生产上线仍需 KMS/Vault/HSM、Redis 高可用、监控告警、漏洞扫描和安全测试。

理解该项目最有效的方法是：先从注解找到框架扩展点，再从扩展点追到 SPI，最后阅读默认实现。不要从工具类逐行向上猜整体设计。
