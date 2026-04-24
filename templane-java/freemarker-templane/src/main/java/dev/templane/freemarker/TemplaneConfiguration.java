package dev.templane.freemarker;

import dev.templane.core.SchemaParser;
import freemarker.template.Configuration;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class TemplaneConfiguration {
    private final Path templateDir;
    private final Configuration fmConfig;
    private final SchemaParser parser = new SchemaParser();

    public TemplaneConfiguration(Path templateDir) {
        this.templateDir = templateDir;
        this.fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        this.fmConfig.setDefaultEncoding("UTF-8");
    }

    /**
     * Load a Templane schema (embedded or sidecar) and compile its body.
     * Sidecar bodies (via {@code body: ./path.ftl}) are resolved relative to
     * the schema file's directory.
     */
    public TemplaneTemplate getTemplate(String name) {
        Path schemaPath = templateDir.resolve(name);
        SchemaParser.Result r = SchemaParser.loadFromPath(schemaPath);
        if (r.error() != null) {
            throw new IllegalArgumentException("Schema load error in " + name + ": " + r.error());
        }
        if (r.body() == null) {
            throw new IllegalArgumentException(
                "Template " + name + " has no renderable body — " +
                "add a '---' separator or a 'body:' key pointing to an external file");
        }
        freemarker.template.Template fmTmpl;
        try {
            fmTmpl = new freemarker.template.Template(name, new StringReader(r.body()), fmConfig);
        } catch (Exception e) {
            throw new RuntimeException("FreeMarker compile error: " + e.getMessage(), e);
        }
        return new TemplaneTemplate(name, r.schema(), fmTmpl);
    }
}
