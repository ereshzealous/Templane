package dev.tsp.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TIRTextNode.class, name = "text"),
    @JsonSubTypes.Type(value = TIRExprNode.class, name = "expr"),
    @JsonSubTypes.Type(value = TIRIfNode.class, name = "if"),
    @JsonSubTypes.Type(value = TIRForeachNode.class, name = "foreach"),
})
public sealed interface TIRNode permits TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode {}
