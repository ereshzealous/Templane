package dev.templane.core.model;
import java.util.Map;
public record TypedSchema(String id, Map<String, TemplaneField> fields) {}
