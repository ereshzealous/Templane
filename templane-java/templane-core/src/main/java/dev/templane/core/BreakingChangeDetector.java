package dev.templane.core;

import dev.templane.core.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BreakingChangeDetector {

    public static List<BreakingChange> detect(TypedSchema oldSchema, TypedSchema newSchema) {
        List<BreakingChange> out = new ArrayList<>();
        detectFields(oldSchema.fields(), newSchema.fields(), "", out);
        return out;
    }

    private static void detectFields(
        Map<String, TemplaneField> oldFields, Map<String, TemplaneField> newFields,
        String prefix, List<BreakingChange> out) {

        for (Map.Entry<String, TemplaneField> e : oldFields.entrySet()) {
            String name = e.getKey();
            TemplaneField oldField = e.getValue();
            String path = prefix.isEmpty() ? name : prefix + "." + name;

            if (!newFields.containsKey(name)) {
                out.add(new BreakingChange("removed_field", path,
                    oldField.type().getClass().getSimpleName(), "<absent>"));
                continue;
            }

            TemplaneField newField = newFields.get(name);

            if (!oldField.required() && newField.required()) {
                out.add(new BreakingChange("required_change", path, "optional", "required"));
            }

            if (!oldField.type().getClass().equals(newField.type().getClass())) {
                out.add(new BreakingChange("type_change", path,
                    oldField.type().getClass().getSimpleName(),
                    newField.type().getClass().getSimpleName()));
                continue;
            }

            if (oldField.type() instanceof EnumType oldEnum
                && newField.type() instanceof EnumType newEnum) {
                Set<String> newValues = new HashSet<>(newEnum.values());
                for (String v : oldEnum.values()) {
                    if (!newValues.contains(v)) {
                        out.add(new BreakingChange("enum_value_removed", path, v, "<removed>"));
                    }
                }
            }

            if (oldField.type() instanceof ObjectType oldObj
                && newField.type() instanceof ObjectType newObj) {
                detectFields(oldObj.fields(), newObj.fields(), path, out);
            }
        }
    }

    private BreakingChangeDetector() {}
}
