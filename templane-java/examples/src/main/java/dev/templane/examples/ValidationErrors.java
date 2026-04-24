package dev.templane.examples;

import dev.templane.core.model.TypeCheckError;
import dev.templane.freemarker.TemplaneConfiguration;
import dev.templane.freemarker.TemplaneTemplate;
import dev.templane.freemarker.TemplaneTemplateException;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 02 — Validation errors: surface every error code in one pass. */
public class ValidationErrors {
    public static void main(String[] args) {
        Path dir = Path.of("src/main/resources/02errors");
        TemplaneConfiguration cfg = new TemplaneConfiguration(dir);
        TemplaneTemplate tmpl = cfg.getTemplate("profile.templane");

        // Intentionally bad data — trips every error code at once.
        Map<String, Object> badData = new HashMap<>();
        // "name" missing             → missing_required_field
        badData.put("age", "thirty");   // type_mismatch
        badData.put("role", "superuser"); // invalid_enum_value
        badData.put("rol", "admin");    // unknown_field → did_you_mean "role"

        try {
            tmpl.render(badData);
        } catch (TemplaneTemplateException exc) {
            System.out.printf("render refused: %d error(s)%n%n", exc.errors().size());
            for (TypeCheckError e : exc.errors()) {
                System.out.printf("  [%s] %s: %s%n", e.code(), e.field(), e.message());
            }
        }
    }
}
