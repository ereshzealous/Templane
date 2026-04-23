package dev.tsp.core.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
public record IfNode(
    Condition condition,
    @JsonProperty("then_branch") List<ASTNode> thenBranch,
    @JsonProperty("else_branch") List<ASTNode> elseBranch
) implements ASTNode {}
