package dev.templane.examples;

import dev.templane.freemarker.TemplaneConfiguration;
import dev.templane.freemarker.TemplaneTemplate;

import java.nio.file.Path;
import java.util.Map;

/** 01 — Hello: load a Templane file, validate, render with FreeMarker. */
public class Hello {
    public static void main(String[] args) {
        Path dir = Path.of("src/main/resources/01hello");
        TemplaneConfiguration cfg = new TemplaneConfiguration(dir);
        TemplaneTemplate tmpl = cfg.getTemplate("greeting.templane");

        String output = tmpl.render(Map.of(
            "name", "Arya",
            "temperature_c", 22,
            "is_morning", true
        ));
        System.out.print(output);
    }
}
