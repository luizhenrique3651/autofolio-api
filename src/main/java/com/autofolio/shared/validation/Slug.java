package com.autofolio.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Formato do slug do portfólio: letras minúsculas, números e hífens, no máximo 255 caracteres
 * (tamanho da coluna). Valores nulos ficam a cargo de @NotBlank.
 */
@Documented
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Pattern(regexp = "^[a-z0-9-]+$")
@Size(max = 255)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Slug {

    /**
     * Mensagem de erro de validação.
     */
    String message() default "O slug deve conter apenas letras minúsculas, números e hífens, "
            + "com no máximo 255 caracteres.";

    /**
     * Grupos de validação.
     */
    Class<?>[] groups() default {};

    /**
     * Carga útil da validação.
     */
    Class<? extends Payload>[] payload() default {};
}
