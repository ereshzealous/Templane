package dev.tsp.core;

import dev.tsp.core.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IRGenerator {

    public static TIRResult generate(
        List<ASTNode> ast, Map<String, Object> data, String schemaId, String templateId) {
        List<TIRNode> nodes = new ArrayList<>(ast.size());
        for (ASTNode n : ast) nodes.add(nodeToTir(n, data));
        return new TIRResult(templateId, schemaId, nodes);
    }

    private static TIRNode nodeToTir(ASTNode node, Map<String, Object> data) {
        return switch (node) {
            case TextNode t -> new TIRTextNode(t.content());
            case ExprNode e -> new TIRExprNode(e.field(), resolve(data, e.field()));
            case IfNode i -> {
                boolean cond = evaluate(i.condition(), data);
                List<ASTNode> branch = cond ? i.thenBranch() : i.elseBranch();
                List<TIRNode> out = new ArrayList<>();
                for (ASTNode n : branch) out.add(nodeToTir(n, data));
                yield new TIRIfNode(cond, out);
            }
            case ForEachNode f -> {
                Object iter = resolve(data, f.iterable());
                List<?> items = iter instanceof List<?> list ? list : List.of();
                List<List<TIRNode>> rendered = new ArrayList<>();
                for (Object item : items) {
                    Map<String, Object> scope = new HashMap<>(data);
                    scope.put(f.varName(), item);
                    List<TIRNode> body = new ArrayList<>();
                    for (ASTNode n : f.body()) body.add(nodeToTir(n, scope));
                    rendered.add(body);
                }
                yield new TIRForeachNode(f.varName(), rendered);
            }
        };
    }

    @SuppressWarnings("unchecked")
    static Object resolve(Map<String, Object> data, String path) {
        Object current = data;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m) || !m.containsKey(part)) return null;
            current = ((Map<String, Object>) m).get(part);
        }
        return current;
    }

    static boolean evaluate(Condition c, Map<String, Object> data) {
        if ("==".equals(c.op())) {
            Object left = resolve(data, c.left());
            return String.valueOf(left).equals(String.valueOf(c.right()));
        }
        return false;
    }

    private IRGenerator() {}
}
