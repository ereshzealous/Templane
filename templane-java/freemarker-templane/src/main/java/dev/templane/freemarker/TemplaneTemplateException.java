package dev.templane.freemarker;

import dev.templane.core.model.TypeCheckError;
import java.util.List;
import java.util.stream.Collectors;

public class TemplaneTemplateException extends RuntimeException {
    private final List<TypeCheckError> errors;

    public TemplaneTemplateException(List<TypeCheckError> errors) {
        super(errors.stream()
            .map(e -> "[" + e.code() + "] " + e.message())
            .collect(Collectors.joining("\n")));
        this.errors = errors;
    }

    public List<TypeCheckError> errors() { return errors; }
}
