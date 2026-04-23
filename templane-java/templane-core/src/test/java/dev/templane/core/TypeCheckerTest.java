package dev.templane.core;

import dev.templane.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeCheckerTest {

    private TypedSchema schemaOf(Object[]... pairs) {
        Map<String, TemplaneField> f = new LinkedHashMap<>();
        for (Object[] p : pairs) {
            String name = (String) p[0];
            TemplaneFieldType type = (TemplaneFieldType) p[1];
            boolean required = (Boolean) p[2];
            f.put(name, new TemplaneField(name, type, required));
        }
        return new TypedSchema("test", f);
    }

    @Test
    void validDataNoErrors() {
        TypedSchema schema = schemaOf(
            new Object[]{"name", new StringType(), true},
            new Object[]{"age", new NumberType(), false});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("name", "Alice", "age", 30));
        assertThat(errors).isEmpty();
    }

    @Test
    void missingRequiredField() {
        TypedSchema schema = schemaOf(
            new Object[]{"name", new StringType(), true},
            new Object[]{"email", new StringType(), true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("name", "Alice"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo("missing_required_field");
        assertThat(errors.get(0).field()).isEqualTo("email");
    }

    @Test
    void typeMismatchNumber() {
        TypedSchema schema = schemaOf(new Object[]{"age", new NumberType(), true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("age", "thirty"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo("type_mismatch");
        assertThat(errors.get(0).message()).contains("number").contains("string");
    }

    @Test
    void invalidEnumValue() {
        TypedSchema schema = schemaOf(new Object[]{"status",
            new EnumType(List.of("active", "inactive")), true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("status", "unknown"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo("invalid_enum_value");
        assertThat(errors.get(0).message()).contains("unknown");
    }

    @Test
    void unknownField() {
        TypedSchema schema = schemaOf(new Object[]{"name", new StringType(), true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("name", "Alice", "extra", "value"));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.code()).isEqualTo("unknown_field");
            assertThat(e.field()).isEqualTo("extra");
        });
    }

    @Test
    void didYouMean() {
        TypedSchema schema = schemaOf(new Object[]{"name", new StringType(), true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("naem", "Alice"));
        assertThat(errors).anySatisfy(e -> assertThat(e.code()).isEqualTo("missing_required_field"));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.code()).isEqualTo("did_you_mean");
            assertThat(e.message()).contains("name");
        });
    }

    @Test
    void nestedObjectTypeError() {
        ObjectType inner = new ObjectType(Map.of(
            "city", new TemplaneField("city", new StringType(), true)));
        TypedSchema schema = schemaOf(new Object[]{"address", inner, true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("address", Map.of("city", 42)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo("type_mismatch");
        assertThat(errors.get(0).field()).isEqualTo("address.city");
    }

    @Test
    void listItemTypeMismatch() {
        TypedSchema schema = schemaOf(new Object[]{"tags",
            new ListType(new StringType()), true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("tags", List.of("hello", 42, "world")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo("type_mismatch");
        assertThat(errors.get(0).field()).isEqualTo("tags[1]");
    }

    @Test
    void errorsCollectedNotShortCircuited() {
        TypedSchema schema = schemaOf(
            new Object[]{"a", new StringType(), true},
            new Object[]{"b", new NumberType(), true});
        List<TypeCheckError> errors = TypeChecker.check(schema, Map.of("a", 1, "b", "x"));
        assertThat(errors).hasSize(2);
    }
}
