package com.tomas.cuaderno.common.errors;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
