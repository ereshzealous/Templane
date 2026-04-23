package dev.tsp.core;

import dev.tsp.core.model.*;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemaParser {

    public record Result(TypedSchema schema, String body, String error) {
        public static Result ok(TypedSchema s) { return new Result(s, null, null); }
        public static Result ok(TypedSchema s, String body) { return new Result(s, body, null); }
        public static Result err(String msg) { return new Result(null, null, msg); }
    }

    private final Yaml yaml = new Yaml();

    public Result parse(String yamlStr, String schemaId) {
        String schemaYaml;
        String body = null;
        int idx = yamlStr.indexOf("\n---\n");
        if (idx >= 0) {
            schemaYaml = yamlStr.substring(0, idx);
            body = yamlStr.substring(idx + 5);
        } else {
            schemaYaml = yamlStr;
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

        Map<String, TSPField> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String name = String.valueOf(e.getKey());
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldDef = e.getValue() instanceof Map<?, ?>
                ? (Map<String, Object>) e.getValue()
                : Map.of();
            fields.put(name, parseField(name, fieldDef));
        }
        TypedSchema schema = new TypedSchema(schemaId, fields);
        return body == null ? Result.ok(schema) : Result.ok(schema, body);
    }

    private TSPField parseField(String name, Map<String, Object> def) {
        String typeStr = String.valueOf(def.getOrDefault("type", "string"));
        boolean required = Boolean.TRUE.equals(def.get("required"));
        return new TSPField(name, parseType(typeStr, def), required);
    }

    @SuppressWarnings("unchecked")
    private TSPFieldType parseType(String typeStr, Map<String, Object> def) {
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
                Map<String, TSPField> sub = new LinkedHashMap<>();
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
