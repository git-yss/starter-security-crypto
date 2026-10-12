package com.example.security.web;

import com.example.security.core.DefaultMasker;
import com.example.security.core.MaskContext;
import com.example.security.core.MaskType;
import com.example.security.core.Masker;
import com.example.security.core.Sensitive;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.io.IOException;
import java.util.List;

public class SensitiveModule extends SimpleModule {
    public SensitiveModule(final Masker masker) {
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializerProvider provider,
                                                              BeanDescription beanDescription,
                                                              List<BeanPropertyWriter> writers) {
                Sensitive classAnnotation = beanDescription.getClassAnnotations().get(Sensitive.class);
                for (BeanPropertyWriter writer : writers) {
                    Sensitive annotation = writer.getAnnotation(Sensitive.class);
                    if (annotation != null || classAnnotation != null) {
                        Sensitive target = annotation == null ? classAnnotation : annotation;
                        if (!target.ignore()) {
                            MaskType type = target.type() == MaskType.DEFAULT
                                    ? MaskType.DEFAULT : target.type();
                            writer.assignSerializer(new MaskingSerializer(masker, type));
                        }
                    }
                }
                return writers;
            }
        });
    }

    private static class MaskingSerializer extends JsonSerializer<Object> {
        private final Masker masker;
        private final MaskType type;

        private MaskingSerializer(Masker masker, MaskType type) {
            this.masker = masker;
            this.type = type;
        }

        @Override
        public void serialize(Object value, JsonGenerator generator,
                              SerializerProvider serializers) throws IOException {
            if (value == null) {
                generator.writeNull();
                return;
            }
            generator.writeString(masker.mask(String.valueOf(value), new MaskContext(type)));
        }
    }
}
