package com.autofolio.shared.exception;

/**
 * Item da propriedade {@code errors} do 400 de validação.
 */
public record FieldValidationError(String field, String message) {
}
