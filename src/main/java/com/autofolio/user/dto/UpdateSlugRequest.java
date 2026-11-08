package com.autofolio.user.dto;

import com.autofolio.shared.validation.Slug;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para troca do slug do portfólio.
 */
public record UpdateSlugRequest(
        @NotBlank(message = "O slug é obrigatório.")
        @Slug
        String slug
) {
}
