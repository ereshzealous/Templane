package dev.tsp.freemarker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TSPConfigurationTest {

    private void write(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void loadAndRenderBasic(@TempDir Path tmp) throws Exception {
        write(tmp, "greet.tsp", "name:\n  type: string\n  required: true\n---\nHello ${name}!");
        TSPConfiguration cfg = new TSPConfiguration(tmp);
        TSPTemplate t = cfg.getTemplate("greet.tsp");
        assertThat(t.render(Map.of("name", "Alice"))).isEqualTo("Hello Alice!");
    }

    @Test
    void renderThrowsOnTypeError(@TempDir Path tmp) throws Exception {
        write(tmp, "age.tsp", "age:\n  type: number\n  required: true\n---\n${age}");
        TSPConfiguration cfg = new TSPConfiguration(tmp);
        TSPTemplate t = cfg.getTemplate("age.tsp");
        assertThatThrownBy(() -> t.render(Map.of("age", "old")))
            .isInstanceOf(TSPTemplateException.class);
    }

    @Test
    void renderThrowsOnMissingRequiredField(@TempDir Path tmp) throws Exception {
        write(tmp, "r.tsp", "name:\n  type: string\n  required: true\n---\n${name}");
        TSPConfiguration cfg = new TSPConfiguration(tmp);
        TSPTemplate t = cfg.getTemplate("r.tsp");
        assertThatThrownBy(() -> t.render(Map.of()))
            .isInstanceOf(TSPTemplateException.class);
    }

    @Test
    void schemaExposed(@TempDir Path tmp) throws Exception {
        write(tmp, "s.tsp", "name:\n  type: string\n  required: true\n---\nHi");
        TSPConfiguration cfg = new TSPConfiguration(tmp);
        TSPTemplate t = cfg.getTemplate("s.tsp");
        assertThat(t.schema().id()).isEqualTo("s.tsp");
        assertThat(t.schema().fields().get("name").required()).isTrue();
    }

    @Test
    void freemarkerLoopWorks(@TempDir Path tmp) throws Exception {
        write(tmp, "loop.tsp",
            "items:\n  type: list\n  items:\n    type: string\n  required: true\n" +
            "---\n<#list items as item>- ${item}\n</#list>");
        TSPConfiguration cfg = new TSPConfiguration(tmp);
        TSPTemplate t = cfg.getTemplate("loop.tsp");
        assertThat(t.render(Map.of("items", List.of("a", "b")))).isEqualTo("- a\n- b\n");
    }

    @Test
    void missingBodyRaisesOnLoad(@TempDir Path tmp) throws Exception {
        write(tmp, "nobody.tsp", "name:\n  type: string\n  required: true\n");
        TSPConfiguration cfg = new TSPConfiguration(tmp);
        assertThatThrownBy(() -> cfg.getTemplate("nobody.tsp"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
