package com.ericsson.commonutil.serialization;

import com.ericsson.commonutil.serialization.JsonDublicate;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.util.NameTransformer;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 *
 * @author aopkarja
 */
public class DublicateIntrospector extends NopAnnotationIntrospector {

    private static final Object EMPTY_SERIALIZER = new JsonSerializer() {

        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {

        }
    };

    private static final Map<String, Object> used = Collections.synchronizedMap(new WeakHashMap<>());

    boolean isHandled(Annotated am) {
        if (am instanceof AnnotatedClass || am instanceof AnnotatedConstructor) {
            return false;
        }
        return am.hasAnnotation(JsonDublicate.class);
    }

    @Override
    public NameTransformer findUnwrappingNameTransformer(AnnotatedMember member) {
        if (!isHandled(member)) {
            return null;
        }
        String group = member.getAnnotation(JsonDublicate.class).value();
        if (used.containsKey(group)) {
            return NameTransformer.NOP;
        }
        return null;
    }

    @Override
    public Object findSerializer(Annotated annotated) {
        if (!isHandled(annotated)) {
            return null;
        }
        String group = annotated.getAnnotation(JsonDublicate.class).value();
        if (used.containsKey(group)) {
            return EMPTY_SERIALIZER;
        }
        used.put(group, annotated);
        return null;
    }
}
