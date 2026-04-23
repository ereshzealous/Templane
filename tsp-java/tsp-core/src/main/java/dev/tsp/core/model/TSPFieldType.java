package dev.tsp.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StringType.class, name = "string"),
    @JsonSubTypes.Type(value = NumberType.class, name = "number"),
    @JsonSubTypes.Type(value = BooleanType.class, name = "boolean"),
    @JsonSubTypes.Type(value = NullType.class, name = "null"),
    @JsonSubTypes.Type(value = EnumType.class, name = "enum"),
    @JsonSubTypes.Type(value = ListType.class, name = "list"),
    @JsonSubTypes.Type(value = ObjectType.class, name = "object"),
})
public sealed interface TSPFieldType
    permits StringType, NumberType, BooleanType, NullType,
            EnumType, ListType, ObjectType {}
