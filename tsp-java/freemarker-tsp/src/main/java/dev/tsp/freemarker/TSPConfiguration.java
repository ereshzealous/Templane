package dev.tsp.freemarker;

import dev.tsp.core.SchemaParser;
import freemarker.template.Configuration;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class TSPConfiguration {
    private final Path templateDir;
    private final Configuration fmConfig;
    private final SchemaParser parser = new SchemaParser();

    public TSPConfiguration(Path templateDir) {
        this.templateDir = templateDir;
        this.fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        this.fmConfig.setDefaultEncoding("UTF-8");
    }

    public TSPTemplate getTemplate(String name) {
        Path source = templateDir.resolve(name);
        String content;
        try {
            content = Files.readString(source);
        } catch (Exception e) {
            throw new RuntimeException("Cannot read template " + name, e);
        }
        SchemaParser.Result r = parser.parse(content, name);
        if (r.error() != null) {
            throw new IllegalArgumentException("Schema parse error in " + name + ": " + r.error());
        }
        if (r.body() == null) {
            throw new IllegalArgumentException("Template " + name + " has no body separator '---'");
        }
        freemarker.template.Template fmTmpl;
        try {
            fmTmpl = new freemarker.template.Template(name, new StringReader(r.body()), fmConfig);
        } catch (Exception e) {
            throw new RuntimeException("FreeMarker compile error: " + e.getMessage(), e);
        }
        return new TSPTemplate(name, r.schema(), fmTmpl);
    }
}
