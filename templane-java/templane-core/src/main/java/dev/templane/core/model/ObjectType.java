package dev.templane.core.model;
import java.util.Map;
public record ObjectType(Map<String, TemplaneField> fields) implements TemplaneFieldType {}
