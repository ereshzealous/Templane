package dev.tsp.core.model;
import java.util.List;
public record EnumType(List<String> values) implements TSPFieldType {}
