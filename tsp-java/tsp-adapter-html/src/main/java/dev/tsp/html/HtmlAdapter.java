package dev.tsp.html;

import dev.tsp.core.model.*;

public final class HtmlAdapter {

    public static String render(TIRResult tir) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- tsp template_id=").append(tir.templateId())
          .append(" schema_id=").append(tir.schemaId()).append(" -->\n");
        for (TIRNode n : tir.nodes()) sb.append(renderNode(n));
        return sb.toString();
    }

    private static String renderNode(TIRNode node) {
        return switch (node) {
            case TIRTextNode t -> t.content();
            case TIRExprNode e -> e.resolved() == null ? "" : escape(String.valueOf(e.resolved()));
            case TIRIfNode i -> {
                StringBuilder s = new StringBuilder();
                for (TIRNode n : i.branch()) s.append(renderNode(n));
                yield s.toString();
            }
            case TIRForeachNode f -> {
                StringBuilder s = new StringBuilder();
                for (java.util.List<TIRNode> item : f.items()) {
                    for (TIRNode n : item) s.append(renderNode(n));
                }
                yield s.toString();
            }
        };
    }

    static String escape(String s) {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }

    private HtmlAdapter() {}
}
