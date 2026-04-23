package dev.templane.core;

import dev.templane.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IRGeneratorTest {

    @Test
    void basicExpr() {
        List<ASTNode> ast = List.of(
            new TextNode("Hello "), new ExprNode("name"), new TextNode("!"));
        TIRResult r = IRGenerator.generate(ast, Map.of("name", "Alice"), "user", "greeting");
        assertThat(r.nodes().get(1)).isEqualTo(new TIRExprNode("name", "Alice"));
    }

    @Test
    void missingPathResolvesToNull() {
        TIRResult r = IRGenerator.generate(List.of(new ExprNode("missing")), Map.of(), "s", "t");
        assertThat(((TIRExprNode) r.nodes().get(0)).resolved()).isNull();
    }

    @Test
    void ifTruePicksThenBranch() {
        List<ASTNode> ast = List.of(new IfNode(
            new Condition("==", "status", "active"),
            List.of(new TextNode("Active")),
            List.of(new TextNode("Inactive"))));
        TIRResult r = IRGenerator.generate(ast, Map.of("status", "active"), "s", "t");
        TIRIfNode node = (TIRIfNode) r.nodes().get(0);
        assertThat(node.condition()).isTrue();
        assertThat(node.branch()).containsExactly(new TIRTextNode("Active"));
    }

    @Test
    void ifFalsePicksElseBranch() {
        List<ASTNode> ast = List.of(new IfNode(
            new Condition("==", "status", "active"),
            List.of(new TextNode("Active")),
            List.of()));
        TIRResult r = IRGenerator.generate(ast, Map.of("status", "inactive"), "s", "t");
        TIRIfNode node = (TIRIfNode) r.nodes().get(0);
        assertThat(node.condition()).isFalse();
        assertThat(node.branch()).isEmpty();
    }

    @Test
    void foreachRendersEachItem() {
        List<ASTNode> ast = List.of(new ForEachNode(
            "tag", "tags", List.of(new ExprNode("tag"))));
        TIRResult r = IRGenerator.generate(ast, Map.of("tags", List.of("py", "ts", "java")), "s", "t");
        TIRForeachNode node = (TIRForeachNode) r.nodes().get(0);
        assertThat(node.items()).hasSize(3);
        assertThat(node.items().get(0)).containsExactly(new TIRExprNode("tag", "py"));
    }

    @Test
    void nestedDottedPath() {
        List<ASTNode> ast = List.of(new ExprNode("user.address.city"));
        TIRResult r = IRGenerator.generate(ast,
            Map.of("user", Map.of("address", Map.of("city", "London"))), "s", "t");
        assertThat(r.nodes().get(0)).isEqualTo(new TIRExprNode("user.address.city", "London"));
    }

    @Test
    void nestedPathMissingSegmentReturnsNull() {
        List<ASTNode> ast = List.of(new ExprNode("user.address.city"));
        TIRResult r = IRGenerator.generate(ast, Map.of("user", Map.of()), "s", "t");
        assertThat(((TIRExprNode) r.nodes().get(0)).resolved()).isNull();
    }

    @Test
    void conditionEquals() {
        List<ASTNode> ast = List.of(new IfNode(
            new Condition("==", "score", "100"),
            List.of(new TextNode("Perfect")),
            List.of()));
        TIRResult r = IRGenerator.generate(ast, Map.of("score", "100"), "s", "t");
        assertThat(((TIRIfNode) r.nodes().get(0)).condition()).isTrue();
    }

    @Test
    void provenance() {
        TIRResult r = IRGenerator.generate(List.of(), Map.of(), "my-schema", "my-template");
        assertThat(r.schemaId()).isEqualTo("my-schema");
        assertThat(r.templateId()).isEqualTo("my-template");
    }
}
