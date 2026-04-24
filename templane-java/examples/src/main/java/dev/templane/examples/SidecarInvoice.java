package dev.templane.examples;

import dev.templane.core.model.TypeCheckError;
import dev.templane.freemarker.TemplaneConfiguration;
import dev.templane.freemarker.TemplaneTemplate;
import dev.templane.freemarker.TemplaneTemplateException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 06 — Sidecar mode (SPEC §4.3).
 *
 * The template body lives in invoice.ftl — plain FreeMarker, nothing
 * Templane-specific. The schema lives beside it in
 * invoice.schema.yaml, referencing the body via `body: ./invoice.ftl`.
 *
 * This is the adoption pattern: an existing FreeMarker codebase keeps its
 * .ftl files unchanged, drops a schema file next to each one, and gains
 * type-checked data without migrating a single template.
 */
public class SidecarInvoice {
    public static void main(String[] args) {
        Path dir = Path.of("src/main/resources/06sidecar");
        TemplaneConfiguration cfg = new TemplaneConfiguration(dir);
        TemplaneTemplate tmpl = cfg.getTemplate("invoice.schema.yaml");

        Map<String, Object> goodData = Map.of(
            "invoice_number", "INV-2026-0042",
            "customer", Map.of(
                "name", "Acme Corp",
                "email", "ap@acme.example"
            ),
            "line_items", List.of(
                Map.of("description", "Annual subscription", "qty", 1, "unit_price", 499.00),
                Map.of("description", "Premium support",     "qty", 1, "unit_price", 149.00),
                Map.of("description", "Onboarding session",  "qty", 3, "unit_price",  95.00)
            ),
            "total", 933.00
        );

        System.out.println("--- Good data: renders cleanly ---");
        System.out.println(tmpl.render(goodData));

        // Same schema, bad data — type-check fires before FreeMarker sees it.
        Map<String, Object> badData = Map.of(
            "invoice_number", "INV-BAD",
            "customer", Map.of("name", "X"),              // email missing
            "line_items", List.of(
                Map.of("description", "x", "qty", "two", "unit_price", 10)  // qty wrong type
            ),
            "total", "free"                                // total wrong type
        );

        System.out.println("--- Bad data: type-check refuses ---");
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
