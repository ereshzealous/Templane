package dev.templane.freemarker;

import dev.templane.core.TypeChecker;
import dev.templane.core.model.TypeCheckError;
import dev.templane.core.model.TypedSchema;
import freemarker.template.Template;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

public class TemplaneTemplate {
    private final String name;
    private final TypedSchema schema;
    private final Template freemarkerTemplate;

    public TemplaneTemplate(String name, TypedSchema schema, Template freemarkerTemplate) {
        this.name = name;
        this.schema = schema;
        this.freemarkerTemplate = freemarkerTemplate;
    }

    public String name() { return name; }
    public TypedSchema schema() { return schema; }

    public String render(Map<String, Object> data) {
        List<TypeCheckError> errors = TypeChecker.check(schema, data);
        if (!errors.isEmpty()) throw new TemplaneTemplateException(errors);
        StringWriter out = new StringWriter();
        try {
            freemarkerTemplate.process(data, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out.toString();
    }
}
