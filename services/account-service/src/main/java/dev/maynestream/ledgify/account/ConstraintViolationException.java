package dev.maynestream.ledgify.account;

import build.buf.protovalidate.exceptions.ValidationException;
import build.buf.validate.Violation;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ConstraintViolationException extends ValidationException {
    private final List<Violation> violations = new ArrayList<>();

    public ConstraintViolationException(String message, List<Violation> violations) {
        super(message);
        this.violations.addAll(violations);
    }

    public ConstraintViolationException(String message, Throwable cause, List<Violation> violations) {
        super(message, cause);
        this.violations.addAll(violations);
    }
}
