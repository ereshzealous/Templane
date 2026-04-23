package dev.templane.core.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
public record TIRForeachNode(
    @JsonProperty("var") String varName,
    List<List<TIRNode>> items
) implements TIRNode {}
