package dev.maynestream.ledgify.validation;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Message;

public interface ValidationService {

    Validator VALIDATOR = new Validator();

    static <T extends Message> void validate(T request) throws ValidationException {
        final ValidationResult validationResult = VALIDATOR.validate(request);
        if (!validationResult.isSuccess()) {
            throw new ConstraintViolationException("Request has constraint violations", validationResult.getViolations());
        }
    }
}
