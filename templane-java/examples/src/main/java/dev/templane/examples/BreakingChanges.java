package dev.templane.examples;

import dev.templane.core.BreakingChangeDetector;
import dev.templane.core.SchemaParser;
import dev.templane.core.model.BreakingChange;
import dev.templane.core.model.TypedSchema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 05 — Breaking-change detection between schema versions. */
public class BreakingChanges {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of("src/main/resources/05breaking");
        TypedSchema oldSchema = loadSchema(dir.resolve("v1.schema.yaml"));
        TypedSchema newSchema = loadSchema(dir.resolve("v2.schema.yaml"));

        List<BreakingChange> changes = BreakingChangeDetector.detect(oldSchema, newSchema);
        if (changes.isEmpty()) {
            System.out.println("no breaking changes");
            return;
        }
        System.out.printf("%d breaking change(s) detected:%n%n", changes.size());
        for (BreakingChange c : changes) {
            System.out.printf("  [%s] %s%n      old: %s%n      new: %s%n",
                c.category(), c.fieldPath(), c.oldValue(), c.newValue());
        }
    }

    private static TypedSchema loadSchema(Path p) throws Exception {
        SchemaParser.Result r = new SchemaParser().parse(Files.readString(p), p.getFileName().toString());
        if (r.error() != null) throw new RuntimeException("parse error: " + r.error());
        return r.schema();
    }
}
