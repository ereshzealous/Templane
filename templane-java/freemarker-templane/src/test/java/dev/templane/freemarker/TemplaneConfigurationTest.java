package dev.templane.freemarker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplaneConfigurationTest {

    private void write(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void loadAndRenderBasic(@TempDir Path tmp) throws Exception {
        write(tmp, "greet.templane", "name:\n  type: string\n  required: true\n---\nHello ${name}!");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        TemplaneTemplate t = cfg.getTemplate("greet.templane");
        assertThat(t.render(Map.of("name", "Alice"))).isEqualTo("Hello Alice!");
    }

    @Test
    void renderThrowsOnTypeError(@TempDir Path tmp) throws Exception {
        write(tmp, "age.templane", "age:\n  type: number\n  required: true\n---\n${age}");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        TemplaneTemplate t = cfg.getTemplate("age.templane");
        assertThatThrownBy(() -> t.render(Map.of("age", "old")))
            .isInstanceOf(TemplaneTemplateException.class);
    }

    @Test
    void renderThrowsOnMissingRequiredField(@TempDir Path tmp) throws Exception {
        write(tmp, "r.templane", "name:\n  type: string\n  required: true\n---\n${name}");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        TemplaneTemplate t = cfg.getTemplate("r.templane");
        assertThatThrownBy(() -> t.render(Map.of()))
            .isInstanceOf(TemplaneTemplateException.class);
    }

    @Test
    void schemaExposed(@TempDir Path tmp) throws Exception {
        write(tmp, "s.templane", "name:\n  type: string\n  required: true\n---\nHi");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        TemplaneTemplate t = cfg.getTemplate("s.templane");
        assertThat(t.schema().id()).isEqualTo("s.templane");
        assertThat(t.schema().fields().get("name").required()).isTrue();
    }

    @Test
    void freemarkerLoopWorks(@TempDir Path tmp) throws Exception {
        write(tmp, "loop.templane",
            "items:\n  type: list\n  items:\n    type: string\n  required: true\n" +
            "---\n<#list items as item>- ${item}\n</#list>");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        TemplaneTemplate t = cfg.getTemplate("loop.templane");
        assertThat(t.render(Map.of("items", List.of("a", "b")))).isEqualTo("- a\n- b\n");
    }

    @Test
    void missingBodyRaisesOnLoad(@TempDir Path tmp) throws Exception {
        write(tmp, "nobody.templane", "name:\n  type: string\n  required: true\n");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        assertThatThrownBy(() -> cfg.getTemplate("nobody.templane"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -----------------------------------------------------------------------
    // External-body schema (SPEC §4.3) — body: key references native template file
    // -----------------------------------------------------------------------

    @Test
    void sidecarLoadsExternalBody(@TempDir Path tmp) throws Exception {
        write(tmp, "email.ftl", "Hi ${name}!");
        write(tmp, "email.templane",
            "body: ./email.ftl\nname:\n  type: string\n  required: true\n");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        TemplaneTemplate t = cfg.getTemplate("email.templane");
        assertThat(t.render(Map.of("name", "Lin"))).isEqualTo("Hi Lin!");
    }

    @Test
    void sidecarTypeCheckStillFires(@TempDir Path tmp) throws Exception {
        write(tmp, "age.ftl", "You are ${age}");
        write(tmp, "age.templane",
            "body: ./age.ftl\nage:\n  type: number\n  required: true\n");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        TemplaneTemplate t = cfg.getTemplate("age.templane");
        assertThatThrownBy(() -> t.render(Map.of("age", "forever")))
            .isInstanceOf(TemplaneTemplateException.class);
    }

    @Test
    void sidecarMissingBodyFileRaises(@TempDir Path tmp) throws Exception {
        write(tmp, "broken.templane",
            "body: ./not-here.ftl\nname:\n  type: string\n  required: true\n");
        TemplaneConfiguration cfg = new TemplaneConfiguration(tmp);
        assertThatThrownBy(() -> cfg.getTemplate("broken.templane"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("body file");
    }
}
