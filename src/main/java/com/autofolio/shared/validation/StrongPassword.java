package com.autofolio.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de senha: mínimo de 8 caracteres, ao menos 1 número e 1 letra maiúscula
 * (e no máximo 72 bytes, limite do BCrypt). Valores nulos ficam a cargo de @NotBlank.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "A senha deve ter no mínimo 8 caracteres e no máximo 72 bytes, com ao menos 1 número e 1 letra maiúscula.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
