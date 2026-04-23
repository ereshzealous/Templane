package dev.templane.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ListType(@JsonProperty("item_type") TemplaneFieldType itemType) implements TemplaneFieldType {}
