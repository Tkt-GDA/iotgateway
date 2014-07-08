package com.ericsson.commonutil.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk7.Jdk7Module;
import com.fasterxml.jackson.module.mrbean.MrBeanModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 *
 * @author aopkarja
 */
public class Test {

    public static void main(String[] args) {
        ObjectMapper JSON_MAPPER = new ObjectMapper();
        JSON_MAPPER.registerModule(new Jdk7Module());
        JSON_MAPPER.registerModule(new ParameterNamesModule());
        JSON_MAPPER.registerModule(new MrBeanModule());

        JSON_MAPPER.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
        JSON_MAPPER.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        JSON_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        JSON_MAPPER.registerModule(new DublicateDetectionModule());
        try {
            System.out.println(JSON_MAPPER.writeValueAsString(new Test()));
        } catch (JsonProcessingException ex) {
            ex.printStackTrace();
        }
    }

    public A a = new A();

    @JsonDublicate("name")
    public String name = "outer";

    public static class A {

        public B b = new B("b");
        public B bb = new B("bb");
    }

    public static class B {

        @JsonDublicate("name")
        public String name;

        private B(String b) {
            this.name = b;
        }
    }


}
