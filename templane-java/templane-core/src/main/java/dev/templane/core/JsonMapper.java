package dev.templane.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class JsonMapper {
    public static final ObjectMapper INSTANCE = new ObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonMapper() {}
}
