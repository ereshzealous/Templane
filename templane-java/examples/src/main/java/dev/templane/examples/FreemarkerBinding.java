package dev.templane.examples;

import dev.templane.freemarker.TemplaneConfiguration;
import dev.templane.freemarker.TemplaneTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 04 — FreeMarker binding: full FreeMarker syntax over validated data. */
public class FreemarkerBinding {
    public static void main(String[] args) {
        Path dir = Path.of("src/main/resources/04freemarker");
        TemplaneConfiguration cfg = new TemplaneConfiguration(dir);
        TemplaneTemplate tmpl = cfg.getTemplate("email.templane");

        String output = tmpl.render(Map.of(
            "user", Map.of("name", "Lin", "is_new", true),
            "unread_count", 4,
            "notifications", List.of(
                Map.of("kind", "mention", "source", "alex"),
                Map.of("kind", "reply",   "source", "Priya"),
                Map.of("kind", "follow",  "source", "design-team"),
                Map.of("kind", "mention", "source", "jamie")
            )
        ));
        System.out.print(output);
    }
}
