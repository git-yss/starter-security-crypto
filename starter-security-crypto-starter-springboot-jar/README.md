# security-crypto-starter

一个基于 JDK 8、Spring Boot 2.7 的应用层安全 Starter Demo。

## 当前实现

- `@Sensitive`：Jackson 返回 JSON 字段脱敏。
- `@ApiCrypto`：请求 Body 解密、响应 Body 加密。
- `@ApiSign`：请求签名验签和时间窗口校验。
- `@EncryptField` 和 `FieldEncryptor`：提供字段密文格式和加解密基础能力；MyBatis TypeHandler/Interceptor 尚未接入。
- 内存防重放：同一客户端的 nonce 只能使用一次。
- `CryptoProvider`：默认使用 JCE AES-GCM 和 HMAC-SHA256，便于 JDK 8 直接运行。
- `KeyProvider`、`Masker`、`AuditPublisher`、`ReplayStore`：均可由业务 Spring Bean 替换。
- 自动装配：引入 `security-crypto-starter` 后自动生效。

默认密钥只用于 Demo，生产环境必须替换为 KMS、密码机或外部安全配置。

## 构建和启动

需要 JDK 8 和 Maven 3.8+：

```bash
mvn clean package
java -jar demo/target/security-crypto-demo-1.0.0-SNAPSHOT.jar
```

## 脱敏示例

```bash
curl http://localhost:8080/demo/users/1
```

结果中的手机号和身份证号会自动脱敏。

## ApiCrypto 示例

客户端先使用和服务端约定的 AES-GCM 密钥加密原始 JSON：

```json
{
  "username": "demo",
  "password": "secret"
}
```

然后发送：

```json
{
  "data": "SC1$AES_GCM$V1$..."
}
```

接口：

```text
POST /demo/secure/login
```

浏览器 Network 面板中看到的是密文，但浏览器运行时仍然可以通过调试查看加密前和解密后的数据。这项能力不能替代 HTTPS。

## ApiSign 示例

打开配置：

```yaml
security:
  crypto:
    api-sign:
      enabled: true
```

签名原文规则为：

```text
HTTP_METHOD
REQUEST_PATH
QUERY_STRING
TIMESTAMP
NONCE
CLIENT_ID
```

当前 Demo 使用 HMAC-SHA256 作为可运行默认实现。生产接入国密时，应通过 `CryptoProvider` 替换为 SM3/SM2 实现。

## 生产接入注意事项

- 不要使用默认测试密钥。
- 不要把密钥写入代码、日志或审计事件。
- 防重放应替换为 Redis 原子 `SET NX EX` 实现。
- 应用层加密必须和 HTTPS 同时使用。
- `@ApiCrypto` 适合 JSON，不默认处理文件上传。
- 当前版本没有接入 MyBatis、Redis、KMS 和 Bouncy Castle，相关接口已预留，不能把 Demo 默认实现直接用于生产。
- 签名 Body 场景需要增加请求缓存包装器后再开启。
- 解密失败、验签失败和重放请求必须拒绝，不得返回原始密文。

完整设计说明见：

`outputs/security-crypto-starter-SPEC.md`
