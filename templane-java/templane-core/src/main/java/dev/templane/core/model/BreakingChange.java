package dev.templane.core.model;
public record BreakingChange(String category, String fieldPath, String oldValue, String newValue) {}
