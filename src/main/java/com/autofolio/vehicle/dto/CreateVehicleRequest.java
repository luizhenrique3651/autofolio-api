package com.autofolio.vehicle.dto;

import com.autofolio.vehicle.domain.FuelType;
import com.autofolio.vehicle.domain.TransmissionType;
import com.autofolio.vehicle.domain.VehicleCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Corpo do cadastro de veículo. O status não faz parte do contrato: todo veículo nasce DRAFT, e
 * um {@code status} enviado pelo cliente é ignorado. A faixa dos anos depende da data atual e é
 * validada no service.
 */
public record CreateVehicleRequest(
        @NotBlank(message = "Marca é obrigatória.")
        @Size(max = 100, message = "Marca deve ter no máximo 100 caracteres.")
        String brand,

        @NotBlank(message = "Modelo é obrigatório.")
        @Size(max = 100, message = "Modelo deve ter no máximo 100 caracteres.")
        String model,

        @NotNull(message = "Ano de fabricação é obrigatório.")
        @Schema(description = "Entre 1900 e o ano atual", example = "2020")
        Integer yearFab,

        @NotNull(message = "Ano do modelo é obrigatório.")
        @Schema(description = "Igual ao ano de fabricação ou o seguinte", example = "2021")
        Integer yearMod,

        @NotNull(message = "Quilometragem é obrigatória.")
        @PositiveOrZero(message = "Quilometragem não pode ser negativa.")
        @Max(value = 9_999_999, message = "Quilometragem acima do limite permitido.")
        Integer km,

        @NotBlank(message = "Cor é obrigatória.")
        @Size(max = 50, message = "Cor deve ter no máximo 50 caracteres.")
        String color,

        @NotBlank(message = "Motor é obrigatório.")
        @Size(max = 100, message = "Motor deve ter no máximo 100 caracteres.")
        @Schema(example = "2.0 TSI 16V")
        String engine,

        @NotNull(message = "Câmbio é obrigatório.")
        TransmissionType transmission,

        @NotNull(message = "Combustível é obrigatório.")
        FuelType fuel,

        @NotNull(message = "Condição é obrigatória.")
        VehicleCondition condition,

        @NotNull(message = "Preço pedido é obrigatório.")
        @DecimalMin(value = "0", inclusive = false,
                message = "Preço pedido deve ser maior que zero.")
        @Digits(integer = 13, fraction = 2,
                message = "Preço pedido aceita até 13 dígitos inteiros e 2 casas decimais.")
        @Schema(example = "89900.00")
        BigDecimal askingPrice,

        @Size(max = 5000, message = "Descrição deve ter no máximo 5000 caracteres.")
        String description) {
}
