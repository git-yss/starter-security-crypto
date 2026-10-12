package com.example.security.mybatis;

import com.example.security.core.EncryptField;
import com.example.security.core.FieldEncryptor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

import java.lang.reflect.Field;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

@Intercepts({
        @Signature(type = ParameterHandler.class, method = "setParameters",
                args = {java.sql.PreparedStatement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleResultSets",
                args = {Statement.class})
})
public class EncryptFieldInterceptor implements Interceptor {
    private final FieldEncryptor fieldEncryptor;

    public EncryptFieldInterceptor(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof ParameterHandler) {
            Object parameter = readParameter(invocation.getTarget());
            transform(parameter, true);
            try {
                return invocation.proceed();
            } finally {
                transform(parameter, false);
            }
        }
        Object result = invocation.proceed();
        transform(result, false);
        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private Object readParameter(Object handler) throws Exception {
        Class<?> type = handler.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField("parameterObject");
                field.setAccessible(true);
                return field.get(handler);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private void transform(Object value, boolean encrypt) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                transform(item, encrypt);
            }
            return;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                transform(item, encrypt);
            }
            return;
        }
        if (value instanceof java.util.Map) {
            for (Object item : ((java.util.Map<?, ?>) value).values()) {
                transform(item, encrypt);
            }
            return;
        }
        if (value instanceof String || value instanceof Number
                || value instanceof Boolean) {
            return;
        }
        for (Field field : allFields(value.getClass())) {
            EncryptField annotation = field.getAnnotation(EncryptField.class);
            if (annotation == null || field.getType() != String.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                String current = (String) field.get(value);
                field.set(value, encrypt
                        ? fieldEncryptor.encrypt(current, annotation)
                        : fieldEncryptor.decrypt(current, annotation));
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to process encrypted field", ex);
            }
        }
    }

    private Field[] allFields(Class<?> type) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<Field>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            java.util.Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }
        return fields.toArray(new Field[fields.size()]);
    }
}
