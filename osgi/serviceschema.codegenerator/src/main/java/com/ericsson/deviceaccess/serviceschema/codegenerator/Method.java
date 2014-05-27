package com.ericsson.deviceaccess.serviceschema.codegenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import static com.ericsson.deviceaccess.serviceschema.codegenerator.JavaHelper.*;

/**
 *
 * @author delma
 */
public class Method implements CodeBlock {

    private AccessModifier accessModifier;
    private final String name;
    private final String type;
    private final List<Param> parameters;
    private final List<String> lines;
    private JavadocBuilder javadoc;

    public Method(String type, String name) {
        this.type = type;
        this.name = name;
        parameters = new ArrayList<>();
        lines = new ArrayList<>();
        accessModifier = AccessModifier.PUBLIC;
        javadoc = null;
    }

    public Method setAccessModifier(AccessModifier modifier) {
        accessModifier = modifier;
        return this;
    }

    public AccessModifier getAccessModifier() {
        return accessModifier;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Method addParameter(String type, String name, String description) {
        return addParameter(new Param(type, name).setDescription(description));
    }

    public Method addParameter(Param parameter) {
        parameters.add(parameter);
        return this;
    }

    @Override
    public Method add(String line) {
        lines.add(line);
        return this;
    }

    @Override
    public Method append(Object object) {
        int index = lines.size() - 1;
        lines.set(index, lines.get(index) + object);
        return this;
    }

    public List<Param> getParameters() {
        return parameters;
    }

    public Iterable<String> getCodeLines() {
        return lines;
    }

    public Method setJavadoc(JavadocBuilder javadoc) {
        this.javadoc = javadoc;
        return this;
    }

    public String build(int indent) {
        StringBuilder builder = new StringBuilder();
        //JAVADOC
        builder.append(new JavadocBuilder(javadoc).append(this::parameterJavadocs).build(indent));
        //METHOD DECLARATION
        String access = accessModifier.get();
        indent(builder, indent).append(access).append(" ").append(type).append(" ").append(name).append("(").append(buildParameters()).append(")").append(" ").append(BLOCK_START).append(LINE_END);
        //CODE
        for (String line : lines) {
            StringBuilder stringBuilder = new StringBuilder(line);
            Matcher matcher = PARAMETER_PATTERN.matcher(line);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                String sub = stringBuilder.substring(start + 1, end);
                if (sub.endsWith(REPLACEMENT_END)) {
                    sub = sub.substring(0, sub.length() - REPLACEMENT_END.length());
                }
                if (sub.isEmpty()) {
                    stringBuilder.replace(start, end, REPLACEMENT_START);
                } else {
                    int index = Integer.parseInt(sub);
                    String methodName = parameters.get(index).getName();
                    stringBuilder.replace(start, end, methodName);
                }
            }
            indent(builder, indent + 1).append(stringBuilder).append(LINE_END);
        }
        indent(builder, indent).append(BLOCK_END).append(LINE_END);
        return builder.toString();
    }

    public JavadocBuilder parameterJavadocs(JavadocBuilder builder) {
        parameters.forEach(p -> builder.parameter(p.getName(), p.getDescription()));
        return builder;
    }

    private StringBuilder buildParameters() {
        StringBuilder builder = new StringBuilder();
        parameters.forEach(p -> builder.append(capitalize(p.getType())).append(" ").append(p.getName().toLowerCase()).append(", "));
        if (builder.length() > 0) {
            builder.setLength(builder.length() - 2);
        }
        return builder;
    }
}
