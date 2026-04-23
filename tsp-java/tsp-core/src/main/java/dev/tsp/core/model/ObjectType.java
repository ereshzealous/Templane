package dev.tsp.core.model;
import java.util.Map;
public record ObjectType(Map<String, TSPField> fields) implements TSPFieldType {}
