package dev.tsp.core.model;
import java.util.Map;
public record TypedSchema(String id, Map<String, TSPField> fields) {}
