package dev.templane.core;

import dev.templane.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaParserTest {

    private final SchemaParser parser = new SchemaParser();

    @Test
    void basicFields() {
        String yaml = "name:\n  type: string\n  required: true\nage:\n  type: number\n  required: false\n";
        SchemaParser.Result r = parser.parse(yaml, "basic");
        assertThat(r.schema()).isNotNull();
        assertThat(r.schema().id()).isEqualTo("basic");
        assertThat(r.schema().fields().get("name").type()).isEqualTo(new StringType());
        assertThat(r.schema().fields().get("name").required()).isTrue();
        assertThat(r.schema().fields().get("age").type()).isEqualTo(new NumberType());
        assertThat(r.schema().fields().get("age").required()).isFalse();
    }

    @Test
    void enumType() {
        String yaml = "status:\n  type: enum\n  values: [active, inactive, pending]\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "enum-type");
        assertThat(r.schema().fields().get("status").type())
            .isEqualTo(new EnumType(List.of("active", "inactive", "pending")));
    }

    @Test
    void listType() {
        String yaml = "tags:\n  type: list\n  items:\n    type: string\n  required: false\n";
        SchemaParser.Result r = parser.parse(yaml, "list-type");
        assertThat(r.schema().fields().get("tags").type())
            .isEqualTo(new ListType(new StringType()));
    }

    @Test
    void objectType() {
        String yaml = "address:\n  type: object\n  required: true\n  fields:\n    city:\n      type: string\n      required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "object-type");
        TemplaneFieldType t = r.schema().fields().get("address").type();
        assertThat(t).isInstanceOf(ObjectType.class);
        Map<String, TemplaneField> inner = ((ObjectType) t).fields();
        assertThat(inner.get("city").type()).isEqualTo(new StringType());
    }

    @Test
    void bodyExtracted() {
        String yaml = "name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n";
        SchemaParser.Result r = parser.parse(yaml, "body-extracted");
        assertThat(r.schema()).isNotNull();
        assertThat(r.body()).isEqualTo("Hello {{ name }}!\n");
    }

    @Test
    void invalidSchemaReturnsError() {
        SchemaParser.Result r = parser.parse("- just\n- a\n- list\n", "invalid-schema");
        assertThat(r.error()).isNotNull();
        assertThat(r.schema()).isNull();
    }

    @Test
    void deepNesting() {
        String yaml = """
            order:
              type: object
              required: true
              fields:
                customer:
                  type: object
                  required: true
                  fields:
                    address:
                      type: object
                      required: true
                      fields:
                        city:
                          type: string
                          required: true
            """;
        SchemaParser.Result r = parser.parse(yaml, "deep-nesting");
        ObjectType outer = (ObjectType) r.schema().fields().get("order").type();
        ObjectType mid = (ObjectType) outer.fields().get("customer").type();
        ObjectType inner = (ObjectType) mid.fields().get("address").type();
        assertThat(inner.fields().get("city").type()).isEqualTo(new StringType());
    }
}
