package com.autofolio.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Substituição completa dos dados editáveis do perfil (PUT): campo ausente limpa o valor. A foto
 * é gerenciada pelo upload (AF-014).
 */
public record UpdateProfileRequest(
        @Size(max = 2000, message = "A bio deve ter no máximo 2000 caracteres.")
        String bio,

        @Pattern(regexp = "^\\+?[0-9]{10,15}$",
                message = "O WhatsApp deve ter de 10 a 15 dígitos, com + opcional no início.")
        String whatsapp,

        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
        String city,

        @Size(max = 10, message = "Informe no máximo 10 especialidades.")
        List<@NotBlank(message = "A especialidade não pode ser vazia.")
        @Size(max = 50, message = "A especialidade deve ter no máximo 50 caracteres.")
        String> specialties,

        @Min(value = 0, message = "Os anos de experiência não podem ser negativos.")
        @Max(value = 80, message = "Os anos de experiência devem ser no máximo 80.")
        Integer expYears
) {
}
