package dev.templane.html;

import dev.templane.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlAdapterTest {

    private TIRResult tir(TIRNode... nodes) {
        return new TIRResult("t", "s", List.of(nodes));
    }

    private TIRResult tir(String tmplId, String schId, TIRNode... nodes) {
        return new TIRResult(tmplId, schId, List.of(nodes));
    }

    @Test
    void htmlBasic() {
        TIRResult t = tir("greeting", "user",
            new TIRTextNode("Hello "),
            new TIRExprNode("name", "Alice"),
            new TIRTextNode("!"));
        assertThat(HtmlAdapter.render(t))
            .isEqualTo("<!-- templane template_id=greeting schema_id=user -->\nHello Alice!");
    }

    @Test
    void htmlEscapesSpecialChars() {
        TIRResult t = tir("escape", "data",
            new TIRExprNode("content", "<b>Hello & World</b>"));
        assertThat(HtmlAdapter.render(t)).contains("&lt;b&gt;Hello &amp; World&lt;/b&gt;");
    }

    @Test
    void htmlDoesNotEscapeTextNodes() {
        TIRResult t = tir(new TIRTextNode("<li>item</li>"));
        assertThat(HtmlAdapter.render(t)).contains("<li>item</li>");
    }

    @Test
    void htmlProvenanceComment() {
        TIRResult t = tir("my-template", "my-schema", new TIRTextNode("Hello"));
        assertThat(HtmlAdapter.render(t))
            .startsWith("<!-- templane template_id=my-template schema_id=my-schema -->");
    }

    @Test
    void htmlFalsyZeroRendersAsString() {
        TIRResult t = tir("counter", "stats",
            new TIRTextNode("Count: "), new TIRExprNode("count", 0));
        assertThat(HtmlAdapter.render(t)).contains("Count: 0");
    }

    @Test
    void htmlNullResolvesToEmpty() {
        TIRResult t = tir(new TIRTextNode("X="), new TIRExprNode("x", null));
        String out = HtmlAdapter.render(t);
        assertThat(out).contains("X=");
        assertThat(out).doesNotContain("null");
    }

    @Test
    void htmlForeach() {
        TIRResult t = tir("list", "data", new TIRForeachNode("item", List.of(
            List.of(new TIRTextNode("<li>"), new TIRExprNode("item", "apple"), new TIRTextNode("</li>")),
            List.of(new TIRTextNode("<li>"), new TIRExprNode("item", "banana"), new TIRTextNode("</li>")))));
        assertThat(HtmlAdapter.render(t)).contains("<li>apple</li><li>banana</li>");
    }
}
