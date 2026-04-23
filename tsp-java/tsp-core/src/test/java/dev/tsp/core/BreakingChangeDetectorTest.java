package dev.tsp.core;

import dev.tsp.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BreakingChangeDetectorTest {

    private TypedSchema s(Object[]... pairs) {
        Map<String, TSPField> f = new LinkedHashMap<>();
        for (Object[] p : pairs) {
            f.put((String) p[0], new TSPField((String) p[0], (TSPFieldType) p[1], (Boolean) p[2]));
        }
        return new TypedSchema("x", f);
    }

    @Test
    void removedFieldIsBreaking() {
        TypedSchema old = s(
            new Object[]{"name", new StringType(), true},
            new Object[]{"email", new StringType(), false});
        TypedSchema nu = s(new Object[]{"name", new StringType(), true});
        List<BreakingChange> changes = BreakingChangeDetector.detect(old, nu);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).category()).isEqualTo("removed_field");
        assertThat(changes.get(0).fieldPath()).isEqualTo("email");
    }

    @Test
    void addedOptionalFieldNotBreaking() {
        TypedSchema old = s(new Object[]{"name", new StringType(), true});
        TypedSchema nu = s(
            new Object[]{"name", new StringType(), true},
            new Object[]{"age", new NumberType(), false});
        assertThat(BreakingChangeDetector.detect(old, nu)).isEmpty();
    }

    @Test
    void optionalToRequiredIsBreaking() {
        TypedSchema old = s(new Object[]{"email", new StringType(), false});
        TypedSchema nu = s(new Object[]{"email", new StringType(), true});
        List<BreakingChange> changes = BreakingChangeDetector.detect(old, nu);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).category()).isEqualTo("required_change");
    }

    @Test
    void requiredToOptionalNotBreaking() {
        TypedSchema old = s(new Object[]{"email", new StringType(), true});
        TypedSchema nu = s(new Object[]{"email", new StringType(), false});
        assertThat(BreakingChangeDetector.detect(old, nu)).isEmpty();
    }

    @Test
    void typeChangeIsBreaking() {
        TypedSchema old = s(new Object[]{"count", new StringType(), true});
        TypedSchema nu = s(new Object[]{"count", new NumberType(), true});
        List<BreakingChange> changes = BreakingChangeDetector.detect(old, nu);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).category()).isEqualTo("type_change");
    }

    @Test
    void enumValueRemovedIsBreaking() {
        TypedSchema old = s(new Object[]{"status",
            new EnumType(List.of("active", "inactive", "pending")), true});
        TypedSchema nu = s(new Object[]{"status",
            new EnumType(List.of("active", "inactive")), true});
        List<BreakingChange> changes = BreakingChangeDetector.detect(old, nu);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).category()).isEqualTo("enum_value_removed");
        assertThat(changes.get(0).oldValue()).isEqualTo("pending");
    }

    @Test
    void enumValueAddedNotBreaking() {
        TypedSchema old = s(new Object[]{"status", new EnumType(List.of("active")), true});
        TypedSchema nu = s(new Object[]{"status",
            new EnumType(List.of("active", "inactive")), true});
        assertThat(BreakingChangeDetector.detect(old, nu)).isEmpty();
    }

    @Test
    void nestedObjectRecursion() {
        ObjectType innerOld = new ObjectType(Map.of(
            "city", new TSPField("city", new StringType(), true),
            "zip",  new TSPField("zip",  new StringType(), false)));
        ObjectType innerNew = new ObjectType(Map.of(
            "city", new TSPField("city", new StringType(), true)));
        TypedSchema old = s(new Object[]{"address", innerOld, true});
        TypedSchema nu = s(new Object[]{"address", innerNew, true});
        List<BreakingChange> changes = BreakingChangeDetector.detect(old, nu);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).category()).isEqualTo("removed_field");
        assertThat(changes.get(0).fieldPath()).isEqualTo("address.zip");
    }
}
