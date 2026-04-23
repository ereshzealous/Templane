package dev.tsp.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextNode.class, name = "text"),
    @JsonSubTypes.Type(value = ExprNode.class, name = "expr"),
    @JsonSubTypes.Type(value = IfNode.class, name = "if"),
    @JsonSubTypes.Type(value = ForEachNode.class, name = "foreach"),
})
public sealed interface ASTNode permits TextNode, ExprNode, IfNode, ForEachNode {}
