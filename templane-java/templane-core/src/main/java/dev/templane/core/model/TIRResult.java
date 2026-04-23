package dev.templane.core.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
public record TIRResult(
    @JsonProperty("template_id") String templateId,
    @JsonProperty("schema_id") String schemaId,
    List<TIRNode> nodes
) {}
