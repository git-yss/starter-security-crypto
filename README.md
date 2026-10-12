security-crypto-starter
基于 JDK 8、Spring Boot 2.7 的应用层安全 Starter Demo。

当前实现
@Sensitive：Jackson 返回 JSON 字段脱敏。
@ApiCrypto：JSON 请求体解密、响应体加密。
@ApiSign：请求签名验签和时间窗口校验。
@EncryptField：字段加密元数据，MyBatis 插件支持常见 POJO 参数和结果对象。
HTTP 操作审计：记录操作员、客户端 IP、服务主机、方法、URI、查询参数、脱敏入参、状态码和耗时。
CryptoProvider、KeyProvider、Masker、AuditPublisher、ReplayStore：均可由业务 Spring Bean 替换。
自动装配：引入 security-crypto-starter 后自动注册。
默认密钥只用于 Demo，生产环境必须替换为 KMS、密码机或外部安全配置。

构建和启动
需要 JDK 8 和 Maven 3.8+：

mvn clean package
java -jar demo/target/security-crypto-demo-1.0.0-SNAPSHOT.jar
患者隐私场景
患者手机号、地址、身份证和特殊感染诊断可以标记为加密字段：

public class PatientEntity {
    @EncryptField
    private String phone;
    @EncryptField
    private String address;
    @EncryptField
    private String idCard;
    @EncryptField
    private String infectionDiagnosis;
}
配置：

security:
  crypto:
    field-encrypt:
      enabled: true
    audit:
      enabled: true
      max-body-length: 4096
展示时建议使用不同 DTO：

PatientMaskedResponse：普通查询使用，手机号、地址、身份证和诊断信息自动脱敏。
PatientPlainResponse：只用于经过权限控制的医疗业务接口。
Demo 接口：

GET /demo/patients/1/masked
GET /demo/patients/1/plain
数据库透明加密的 MyBatis 插件已经提供。使用 MyBatis 的业务项目需要额外引入 security-crypto-mybatis 和 MyBatis 依赖，自动配置会在检测到 MyBatis 后注册插件。复杂动态 SQL、Map 参数、联表 DTO 和批量参数需要单独验证。

本 Starter 只负责加密和脱敏，不负责判断当前医生是否有权查看明文。明文接口必须由业务项目的认证、RBAC、数据权限或破窗审批逻辑保护。

HTTP 操作审计
默认开启审计过滤器，日志使用 SECURITY_AUDIT logger，主要字段包括：

operatorId
clientIp
serverHost
httpMethod
requestUri
queryString
requestBody
responseStatus
durationMs
业务项目可以实现 OperatorContextProvider：

@Component
public class LoginOperatorProvider implements OperatorContextProvider {
    @Override
    public String getOperatorId() {
        return LoginContext.currentUserId();
    }
}
如果业务没有提供操作员上下文，组件不会伪造账号，只会尝试读取 X-Operator-Id 请求头用于 Demo。生产环境必须从可信登录上下文获取，不能信任客户端自行传入的账号头。

审计入参会对密码、Token、Authorization、身份证、手机号、地址、诊断和感染相关字段进行脱敏。非 JSON Body 只记录 [non-json-body]。

ApiCrypto
客户端先使用约定的 AES-GCM 密钥加密原始 JSON：

{
  "username": "demo",
  "password": "secret"
}
再发送：

{
  "data": "SC1$AES_GCM$V1$..."
}
接口：

POST /demo/secure/login
浏览器 Network 面板中看到的是密文，但浏览器运行时仍然可以通过调试查看加密前和解密后的数据。该能力不能替代 HTTPS。

生产限制
不要使用默认测试密钥。
不要把密钥写入代码、日志或审计事件。
防重放应替换为 Redis 原子 SET NX EX 实现。
当前默认 JCE 实现使用 AES-GCM 和 HMAC-SHA256，生产国密场景应替换为 SM2/SM3/SM4 Provider。
明文展示接口必须由业务权限控制。
解密失败、验签失败和重放请求必须拒绝。
完整设计说明见 outputs/security-crypto-starter-SPEC.md。
