package dev.tsp.core.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
public record ForEachNode(
    @JsonProperty("var") String varName,
    String iterable,
    List<ASTNode> body
) implements ASTNode {}
