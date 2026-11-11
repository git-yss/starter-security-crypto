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
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 对 {@link EncryptField} 字段进行透明加解密的 MyBatis 插件。
 *
 * <p>写操作时，在 MyBatis 绑定 PreparedStatement 参数期间临时把字段替换为密文，
 * 并在 {@code finally} 中恢复原值；读操作时，在 MyBatis 完成结果集映射后把密文字段
 * 解密为明文。</p>
 */
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
            List<FieldChange> changes = new ArrayList<FieldChange>();
            // 同一个对象可能被 Map 中多个键重复引用，按对象身份去重可避免重复加密。
            encryptForWrite(parameter, new IdentityHashMap<Object, Boolean>(), changes);
            try {
                return invocation.proceed();
            } finally {
                // SQL 执行完成后精确恢复原值，避免调用方持有的对象意外变成密文。
                restore(changes);
            }
        }
        Object result = invocation.proceed();
        decryptResults(result, new IdentityHashMap<Object, Boolean>());
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

    private void encryptForWrite(Object value, IdentityHashMap<Object, Boolean> visited,
                                 List<FieldChange> changes) {
        if (!mark(value, visited)) {
            return;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                encryptForWrite(item, visited, changes);
            }
            return;
        }
        if (value instanceof java.util.Map) {
            for (Object item : ((java.util.Map<?, ?>) value).values()) {
                encryptForWrite(item, visited, changes);
            }
            return;
        }
        processFields(value, true, changes);
    }

    private void decryptResults(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (!mark(value, visited)) {
            return;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                decryptResults(item, visited);
            }
            return;
        }
        if (value instanceof java.util.Map) {
            for (Object item : ((java.util.Map<?, ?>) value).values()) {
                decryptResults(item, visited);
            }
            return;
        }
        processFields(value, false, null);
    }

    private boolean mark(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return false;
        }
        if (value instanceof String || value instanceof Number
                || value instanceof Boolean || value.getClass().isEnum()) {
            return false;
        }
        return visited.put(value, Boolean.TRUE) == null;
    }

    private void processFields(Object value, boolean encrypt, List<FieldChange> changes) {
        for (Field field : allFields(value.getClass())) {
            EncryptField annotation = field.getAnnotation(EncryptField.class);
            if (annotation == null || field.getType() != String.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                String current = (String) field.get(value);
                if (encrypt) {
                    String encrypted = fieldEncryptor.encrypt(current, annotation);
                    if (current != encrypted && (current == null || !current.equals(encrypted))) {
                        changes.add(new FieldChange(value, field, current));
                        field.set(value, encrypted);
                    }
                } else if (current != null) {
                    field.set(value, fieldEncryptor.decrypt(current, annotation));
                }
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to process encrypted field", ex);
            }
        }
    }

    private void restore(List<FieldChange> changes) {
        for (int i = changes.size() - 1; i >= 0; i--) {
            FieldChange change = changes.get(i);
            try {
                change.field.set(change.target, change.originalValue);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Failed to restore encrypted field", ex);
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

    private static final class FieldChange {
        private final Object target;
        private final Field field;
        private final String originalValue;

        private FieldChange(Object target, Field field, String originalValue) {
            this.target = target;
            this.field = field;
            this.originalValue = originalValue;
        }
    }
}
