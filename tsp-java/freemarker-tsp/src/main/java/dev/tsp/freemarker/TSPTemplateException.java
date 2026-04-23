package dev.tsp.freemarker;

import dev.tsp.core.model.TypeCheckError;
import java.util.List;
import java.util.stream.Collectors;

public class TSPTemplateException extends RuntimeException {
    private final List<TypeCheckError> errors;

    public TSPTemplateException(List<TypeCheckError> errors) {
        super(errors.stream()
            .map(e -> "[" + e.code() + "] " + e.message())
            .collect(Collectors.joining("\n")));
        this.errors = errors;
    }

    public List<TypeCheckError> errors() { return errors; }
}
