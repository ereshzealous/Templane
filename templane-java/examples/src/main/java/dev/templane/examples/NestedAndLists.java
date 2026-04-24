package dev.templane.examples;

import dev.templane.freemarker.TemplaneConfiguration;
import dev.templane.freemarker.TemplaneTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 03 — Nested objects, enums, lists: an order receipt. */
public class NestedAndLists {
    public static void main(String[] args) {
        Path dir = Path.of("src/main/resources/03nested");
        TemplaneConfiguration cfg = new TemplaneConfiguration(dir);
        TemplaneTemplate tmpl = cfg.getTemplate("order.templane");

        String output = tmpl.render(Map.of(
            "customer", Map.of(
                "name", "Jordan Shah",
                "tier", "pro"
            ),
            "items", List.of(
                Map.of("sku", "BOOK-042", "qty", 2),
                Map.of("sku", "PEN-003",  "qty", 5),
                Map.of("sku", "MUG-099",  "qty", 1)
            ),
            "total_cents", 5993
        ));
        System.out.print(output);
    }
}
