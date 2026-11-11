package com.example.security.mybatis;

import com.example.security.core.EncryptField;
import com.example.security.core.FieldEncryptor;
import com.example.security.core.JceCryptoProvider;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptFieldInterceptorTest {
    @Test
    void restoresOriginalValueAndHandlesDuplicateReferences() throws Throwable {
        Entity entity = new Entity("13812345678");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("first", entity);
        parameters.put("duplicate", entity);
        CapturingParameterHandler handler = new CapturingParameterHandler(parameters, entity);
        Method method = ParameterHandler.class.getMethod("setParameters", PreparedStatement.class);
        Invocation invocation = new Invocation(handler, method, new Object[] {null});
        FieldEncryptor encryptor = new FieldEncryptor(new JceCryptoProvider(
                version -> "1234567890123456".getBytes(StandardCharsets.UTF_8)));

        new EncryptFieldInterceptor(encryptor).intercept(invocation);

        assertTrue(handler.valueSeenDuringSql.startsWith("SC1$AES_GCM$V1$"));
        assertEquals("13812345678", entity.phone);
    }

    private static class Entity {
        @EncryptField
        private String phone;

        private Entity(String phone) {
            this.phone = phone;
        }
    }

    private static class CapturingParameterHandler implements ParameterHandler {
        private final Object parameterObject;
        private final Entity entity;
        private String valueSeenDuringSql;

        private CapturingParameterHandler(Object parameterObject, Entity entity) {
            this.parameterObject = parameterObject;
            this.entity = entity;
        }

        @Override
        public Object getParameterObject() {
            return parameterObject;
        }

        @Override
        public void setParameters(PreparedStatement ps) throws SQLException {
            valueSeenDuringSql = entity.phone;
        }
    }
}
