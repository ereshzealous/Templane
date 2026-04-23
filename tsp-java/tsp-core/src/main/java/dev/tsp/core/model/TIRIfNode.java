package dev.tsp.core.model;
import java.util.List;
public record TIRIfNode(boolean condition, List<TIRNode> branch) implements TIRNode {}
