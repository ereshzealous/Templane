package dev.templane.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for SPEC §4.3 — sidecar mode. Mirrors test_sidecar.py. */
class SidecarParseTest {

    private final SchemaParser parser = new SchemaParser();

    @Test
    void sidecarBodyPathRecognized() {
        String yaml = "body: ./email.jinja\nname:\n  type: string\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "sidecar-basic");
        assertThat(r.error()).isNull();
        assertThat(r.bodyPath()).isEqualTo("./email.jinja");
        // body: key must NOT be treated as a field
        assertThat(r.schema().fields()).doesNotContainKey("body");
        assertThat(r.schema().fields().keySet()).containsExactly("name");
    }

    @Test
    void sidecarEngineExplicitWins() {
        String yaml = "body: ./t.hbs\nengine: jinja\nuser:\n  type: string\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "engine-explicit");
        assertThat(r.error()).isNull();
        // explicit engine wins even when extension disagrees
        assertThat(r.engine()).isEqualTo("jinja");
    }

    @Test
    void sidecarEngineInferredFromExtension() {
        String[][] cases = {
            {"./t.jinja", "jinja"},
            {"./t.hbs", "handlebars"},
            {"./t.ftl", "freemarker"},
            {"./t.tmpl", "gotemplate"},
            {"./t.md", "markdown"},
            {"./t.html", "html-raw"},
        };
        for (String[] c : cases) {
            String bodyPath = c[0];
            String expectedEngine = c[1];
            String yaml = "body: " + bodyPath + "\nname:\n  type: string\n  required: true\n";
            SchemaParser.Result r = parser.parse(yaml, "inferred");
            assertThat(r.error()).as("error for %s", bodyPath).isNull();
            assertThat(r.engine()).as("engine for %s", bodyPath).isEqualTo(expectedEngine);
        }
    }

    @Test
    void noExtensionMeansNoEngineInference() {
        String yaml = "body: ./t\nname:\n  type: string\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "no-ext");
        assertThat(r.error()).isNull();
        // no extension → no inference, no error (engine optional)
        assertThat(r.engine()).isNull();
    }

    @Test
    void unknownEngineRejected() {
        String yaml = "body: ./t.jinja\nengine: mystery\nname:\n  type: string\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "bad-engine");
        assertThat(r.error()).isNotNull().contains("mystery");
    }

    @Test
    void absolutePathRejected() {
        String yaml = "body: /etc/passwd\nname:\n  type: string\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "abs-path");
        assertThat(r.error()).isNotNull();
        assertThat(r.error().toLowerCase()).contains("relative");
    }

    @Test
    void parentEscapeRejected() {
        String yaml = "body: ../../../etc/passwd\nname:\n  type: string\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "escape");
        assertThat(r.error()).isNotNull();
    }

    @Test
    void sidecarAndSeparatorConflictRejected() {
        String yaml = "body: ./a.jinja\nname:\n  type: string\n  required: true\n---\nHello\n";
        SchemaParser.Result r = parser.parse(yaml, "conflict");
        assertThat(r.error()).isNotNull();
        assertThat(r.error().toLowerCase()).contains("both");
    }

    @Test
    void embeddedModeUnchanged() {
        // Backward compat: a 1.0 schema with --- separator still works.
        String yaml = "name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n";
        SchemaParser.Result r = parser.parse(yaml, "embedded");
        assertThat(r.error()).isNull();
        assertThat(r.bodyPath()).isNull();
        assertThat(r.engine()).isNull();
        assertThat(r.body()).isEqualTo("Hello {{ name }}!\n");
    }

    @Test
    void checkOnlyMode() {
        // Schema with neither body: key nor --- → check-only, no body emitted.
        String yaml = "name:\n  type: string\n  required: true\n";
        SchemaParser.Result r = parser.parse(yaml, "check-only");
        assertThat(r.error()).isNull();
        assertThat(r.body()).isNull();
        assertThat(r.bodyPath()).isNull();
    }

    @Test
    void loadFromPathResolvesSidecar(@TempDir Path tmp) throws Exception {
        Path body = tmp.resolve("greeting.jinja");
        Files.writeString(body, "Hello {{ name }}!\n");
        Path schema = tmp.resolve("greeting.templane");
        Files.writeString(schema, "body: ./greeting.jinja\nname:\n  type: string\n  required: true\n");

        SchemaParser.Result r = SchemaParser.loadFromPath(schema);
        assertThat(r.error()).isNull();
        assertThat(r.bodyPath()).isEqualTo("./greeting.jinja");
        assertThat(r.body()).isEqualTo("Hello {{ name }}!\n");
        assertThat(r.engine()).isEqualTo("jinja");
    }

    @Test
    void loadFromPathMissingBodyFile(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("broken.templane");
        Files.writeString(schema, "body: ./nope.jinja\nname:\n  type: string\n  required: true\n");
        SchemaParser.Result r = SchemaParser.loadFromPath(schema);
        assertThat(r.error()).isNotNull();
        assertThat(r.error().toLowerCase()).contains("body file");
    }

    @Test
    void loadFromPathEmbeddedMode(@TempDir Path tmp) throws Exception {
        // load_from_path on an embedded schema should work the same as parse().
        Path schema = tmp.resolve("embedded.templane");
        Files.writeString(schema, "name:\n  type: string\n  required: true\n---\nHi {{ name }}\n");
        SchemaParser.Result r = SchemaParser.loadFromPath(schema);
        assertThat(r.error()).isNull();
        assertThat(r.body()).isEqualTo("Hi {{ name }}\n");
        assertThat(r.bodyPath()).isNull();
    }
}
