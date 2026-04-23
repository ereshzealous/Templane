package dev.tsp.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ListType(@JsonProperty("item_type") TSPFieldType itemType) implements TSPFieldType {}
