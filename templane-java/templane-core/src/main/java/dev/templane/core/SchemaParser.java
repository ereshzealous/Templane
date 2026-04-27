package dev.templane.core;

import dev.templane.core.model.*;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SchemaParser {

    private static final Set<String> VALID_ENGINES = Set.of(
        "jinja", "handlebars", "freemarker", "gotemplate",
        "markdown", "html-raw", "yaml-raw"
    );

    private static final Map<String, String> ENGINE_BY_EXT = Map.ofEntries(
        Map.entry(".jinja", "jinja"),
        Map.entry(".jinja2", "jinja"),
        Map.entry(".j2", "jinja"),
        Map.entry(".hbs", "handlebars"),
        Map.entry(".handlebars", "handlebars"),
        Map.entry(".ftl", "freemarker"),
        Map.entry(".ftlh", "freemarker"),
        Map.entry(".tmpl", "gotemplate"),
        Map.entry(".gotmpl", "gotemplate"),
        Map.entry(".md", "markdown"),
        Map.entry(".markdown", "markdown"),
        Map.entry(".html", "html-raw"),
        Map.entry(".htm", "html-raw"),
        Map.entry(".yaml", "yaml-raw"),
        Map.entry(".yml", "yaml-raw")
    );

    public record Result(TypedSchema schema, String body, String error, String bodyPath, String engine) {
        public static Result ok(TypedSchema s) { return new Result(s, null, null, null, null); }
        public static Result ok(TypedSchema s, String body) { return new Result(s, body, null, null, null); }
        public static Result ok(TypedSchema s, String body, String bodyPath, String engine) {
            return new Result(s, body, null, bodyPath, engine);
        }
        public static Result err(String msg) { return new Result(null, null, msg, null, null); }
    }

    private final Yaml yaml = new Yaml();

    public Result parse(String yamlStr, String schemaId) {
        String schemaYaml;
        String body = null;
        boolean hasSeparator;
        int idx = yamlStr.indexOf("\n---\n");
        if (idx >= 0) {
            schemaYaml = yamlStr.substring(0, idx);
            body = yamlStr.substring(idx + 5);
            hasSeparator = true;
        } else {
            schemaYaml = yamlStr;
            hasSeparator = false;
        }

        Object data;
        try {
            data = yaml.load(schemaYaml);
        } catch (RuntimeException e) {
            return Result.err(e.getMessage());
        }

        if (!(data instanceof Map<?, ?> map)) {
            return Result.err("Schema must be a YAML mapping");
        }

        String bodyPath = null;
        String engine = null;
        Map<Object, Object> remaining = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object key = e.getKey();
            if ("body".equals(key)) {
                bodyPath = e.getValue() == null ? null : String.valueOf(e.getValue());
            } else if ("engine".equals(key)) {
                engine = e.getValue() == null ? null : String.valueOf(e.getValue());
            } else {
                remaining.put(key, e.getValue());
            }
        }

        if (bodyPath != null && hasSeparator) {
            return Result.err("cannot use both 'body:' key and '---' separator");
        }

        if (bodyPath != null) {
            if (bodyPath.startsWith("/") || hasParentTraversal(bodyPath)) {
                return Result.err("body path must be relative and inside the schema's directory");
            }
        }

        if (engine != null && !VALID_ENGINES.contains(engine)) {
            return Result.err("unknown engine '" + engine + "'");
        }

        if (engine == null && bodyPath != null) {
            String ext = extensionOf(bodyPath);
            if (ext != null) {
                engine = ENGINE_BY_EXT.get(ext);
            }
        }

        Map<String, TemplaneField> fields = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> e : remaining.entrySet()) {
            String name = String.valueOf(e.getKey());
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldDef = e.getValue() instanceof Map<?, ?>
                ? (Map<String, Object>) e.getValue()
                : Map.of();
            fields.put(name, parseField(name, fieldDef));
        }
        TypedSchema schema = new TypedSchema(schemaId, fields);
        return new Result(schema, body, null, bodyPath, engine);
    }

    /**
     * Load a schema from a filesystem path. If the schema uses sidecar mode
     * (has a {@code body:} key), resolves the body file relative to the schema's
     * parent directory and populates the result's {@code body} field.
     */
    public static Result loadFromPath(Path schemaPath) {
        String content;
        try {
            content = Files.readString(schemaPath);
        } catch (IOException e) {
            return Result.err("cannot read schema file: " + e.getMessage());
        }

        String schemaId = schemaPath.getFileName().toString();
        Result r = new SchemaParser().parse(content, schemaId);
        if (r.error() != null) {
            return r;
        }

        if (r.bodyPath() != null && r.body() == null) {
            Path parent = schemaPath.toAbsolutePath().getParent();
            Path resolved = parent == null
                ? Paths.get(r.bodyPath())
                : parent.resolve(r.bodyPath()).normalize();
            String bodyContent;
            try {
                bodyContent = Files.readString(resolved);
            } catch (IOException e) {
                return Result.err("cannot read body file '" + r.bodyPath() + "': " + e.getMessage());
            }
            return new Result(r.schema(), bodyContent, null, r.bodyPath(), r.engine());
        }

        return r;
    }

    private static boolean hasParentTraversal(String bodyPath) {
        // Match Python's Path(body_path).parts check for ".."
        String normalized = bodyPath.replace('\\', '/');
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) return true;
        }
        return false;
    }

    private static String extensionOf(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null; // no dot or leading dot (hidden file with no ext)
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private TemplaneField parseField(String name, Map<String, Object> def) {
        String typeStr = String.valueOf(def.getOrDefault("type", "string"));
        boolean required = Boolean.TRUE.equals(def.get("required"));
        return new TemplaneField(name, parseType(typeStr, def), required);
    }

    @SuppressWarnings("unchecked")
    private TemplaneFieldType parseType(String typeStr, Map<String, Object> def) {
        return switch (typeStr) {
            case "string" -> new StringType();
            case "number" -> new NumberType();
            case "boolean" -> new BooleanType();
            case "null" -> new NullType();
            case "enum" -> {
                List<?> rawValues = def.get("values") instanceof List<?> l ? l : List.of();
                List<String> values = new ArrayList<>();
                for (Object v : rawValues) values.add(String.valueOf(v));
                yield new EnumType(values);
            }
            case "list" -> {
                Map<String, Object> items = def.get("items") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
                yield new ListType(parseType(String.valueOf(items.getOrDefault("type", "string")), items));
            }
            case "object" -> {
                Map<String, TemplaneField> sub = new LinkedHashMap<>();
                Map<String, Object> subDefs = def.get("fields") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
                for (Map.Entry<String, Object> e : subDefs.entrySet()) {
                    Map<String, Object> fd = e.getValue() instanceof Map<?, ?> fm
                        ? (Map<String, Object>) fm : Map.of();
                    sub.put(e.getKey(), parseField(e.getKey(), fd));
                }
                yield new ObjectType(sub);
            }
            default -> new StringType();
        };
    }
}
