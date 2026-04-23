package dev.tsp.yaml;

import dev.tsp.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YamlAdapterTest {

    @Test
    void yamlBasic() {
        TIRResult t = new TIRResult("greeting", "user", List.of(
            new TIRTextNode("name: "), new TIRExprNode("name", "Alice")));
        assertThat(YamlAdapter.render(t))
            .isEqualTo("# tsp template_id=greeting schema_id=user\nname: Alice");
    }

    @Test
    void yamlDoesNotEscape() {
        TIRResult t = new TIRResult("t", "s", List.of(
            new TIRExprNode("content", "<b>Hello</b>")));
        assertThat(YamlAdapter.render(t)).contains("<b>Hello</b>");
    }

    @Test
    void yamlProvenanceComment() {
        TIRResult t = new TIRResult("my-template", "my-schema", List.of(new TIRTextNode("Hello")));
        assertThat(YamlAdapter.render(t))
            .startsWith("# tsp template_id=my-template schema_id=my-schema");
    }
}
