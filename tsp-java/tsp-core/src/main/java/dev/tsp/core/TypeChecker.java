package dev.tsp.core;

import dev.tsp.core.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TypeChecker {

    public static List<TypeCheckError> check(TypedSchema schema, Map<String, Object> data) {
        return check(schema, data, "");
    }

    public static List<TypeCheckError> check(TypedSchema schema, Map<String, Object> data, String prefix) {
        List<TypeCheckError> errors = new ArrayList<>();

        for (Map.Entry<String, TSPField> e : schema.fields().entrySet()) {
            String name = e.getKey();
            TSPField field = e.getValue();
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            if (!data.containsKey(name)) {
                if (field.required()) {
                    errors.add(new TypeCheckError(
                        "missing_required_field", path,
                        "Required field '" + path + "' is missing"));
                }
            } else {
                errors.addAll(checkType(field.type(), data.get(name), path));
            }
        }

        for (String key : data.keySet()) {
            if (!schema.fields().containsKey(key)) {
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                String closest = null;
                int minDist = Integer.MAX_VALUE;
                for (String k : schema.fields().keySet()) {
                    int d = levenshtein(key, k);
                    if (d < minDist) { minDist = d; closest = k; }
                }
                if (closest != null && minDist <= 3) {
                    errors.add(new TypeCheckError(
                        "did_you_mean", path,
                        "Unknown field '" + path + "'. Did you mean '" + closest + "'?"));
                } else {
                    errors.add(new TypeCheckError(
                        "unknown_field", path,
                        "Field '" + path + "' is not defined in schema"));
                }
            }
        }

        return errors;
    }

    @SuppressWarnings("unchecked")
    private static List<TypeCheckError> checkType(TSPFieldType type, Object value, String path) {
        List<TypeCheckError> errors = new ArrayList<>();
        switch (type) {
            case StringType s -> {
                if (!(value instanceof String)) {
                    errors.add(new TypeCheckError("type_mismatch", path,
                        "Field '" + path + "' expected string, got " + typeName(value)));
                }
            }
            case NumberType n -> {
                if (value instanceof Boolean || !(value instanceof Number)) {
                    errors.add(new TypeCheckError("type_mismatch", path,
                        "Field '" + path + "' expected number, got " + typeName(value)));
                }
            }
            case BooleanType b -> {
                if (!(value instanceof Boolean)) {
                    errors.add(new TypeCheckError("type_mismatch", path,
                        "Field '" + path + "' expected boolean, got " + typeName(value)));
                }
            }
            case NullType nl -> {
                if (value != null) {
                    errors.add(new TypeCheckError("type_mismatch", path,
                        "Field '" + path + "' expected null, got " + typeName(value)));
                }
            }
            case EnumType en -> {
                if (!(value instanceof String sv) || !en.values().contains(sv)) {
                    errors.add(new TypeCheckError("invalid_enum_value", path,
                        "Field '" + path + "' value '" + value + "' not in enum [" + String.join(", ", en.values()) + "]"));
                }
            }
            case ListType lt -> {
                if (!(value instanceof List<?> list)) {
                    errors.add(new TypeCheckError("type_mismatch", path,
                        "Field '" + path + "' expected list, got " + typeName(value)));
                } else {
                    for (int i = 0; i < list.size(); i++) {
                        errors.addAll(checkType(lt.itemType(), list.get(i), path + "[" + i + "]"));
                    }
                }
            }
            case ObjectType ot -> {
                if (!(value instanceof Map<?, ?> m)) {
                    errors.add(new TypeCheckError("type_mismatch", path,
                        "Field '" + path + "' expected object, got " + typeName(value)));
                } else {
                    TypedSchema sub = new TypedSchema("", ot.fields());
                    errors.addAll(check(sub, (Map<String, Object>) m, path));
                }
            }
        }
        return errors;
    }

    private static String typeName(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean) return "boolean";
        if (v instanceof Number) return "number";
        if (v instanceof String) return "string";
        if (v instanceof List<?>) return "list";
        if (v instanceof Map<?, ?>) return "object";
        return v.getClass().getSimpleName();
    }

    static int levenshtein(String s1, String s2) {
        int m = s1.length(), n = s2.length();
        int[] dp = new int[n + 1];
        for (int i = 0; i <= n; i++) dp[i] = i;
        for (int i = 1; i <= m; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= n; j++) {
                int temp = dp[j];
                dp[j] = s1.charAt(i - 1) == s2.charAt(j - 1)
                    ? prev
                    : 1 + Math.min(Math.min(prev, dp[j]), dp[j - 1]);
                prev = temp;
            }
        }
        return dp[n];
    }

    private TypeChecker() {}
}
